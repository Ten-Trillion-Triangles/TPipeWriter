"""
Minimal JDWP client for verifying TPipeWriter personality runtime overrides.

Drives the JDWP wire protocol by hand to:
  1. Set a breakpoint at a target method.
  2. Resume the VM.
  3. When the breakpoint hits, capture the event details and the relevant
     Env field values.

This avoids needing jdb (not installed in the SDKMAN java distributions)
and the JDWP MCP server (unstable). Hand-rolled protocol per the JDWP
spec: https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html

Wire format:
  - Server sends 11-byte handshake on connect: "JDWP-Handshake"
  - Client replies with the same 11 bytes.
  - After handshake, length-prefixed packets: 4-byte length (big-endian, MSB
    reserved 0), 4-byte id, 1-byte flags, 1-byte command-set, 1-byte command,
    payload.

Implemented commands:
  - VirtualMachine.Version (1/1)
  - EventRequest.Set (15/1) with kind=2 (BREAKPOINT), suspendPolicy=1 (ALL)
  - EventRequest.Clear (15/2)
  - ClassType.GetReferrerClasses (16/5) [not used]
  - ReferenceType.Fields (2/4) — to get field IDs of Globals.Env
  - Event.Composite (64/100) — breakpoint hit event
"""
import socket
import struct
import sys
import time


class JDWPClient:
    def __init__(self, host, port):
        self.sock = socket.create_connection((host, port), timeout=10)
        self.sock.settimeout(5)
        self.next_id = 1
        # JDWP handshake: client sends "JDWP-Handshake" (14 bytes) first, then
        # reads it back from the server. The server is passive until the
        # client speaks.
        self.sock.sendall(b"JDWP-Handshake")
        got = self.sock.recv(14)
        assert got == b"JDWP-Handshake", f"unexpected handshake: {got!r}"
        # Read all available threads
        self._vm_start_time = time.time()

    def _recv_packet(self):
        """Receive one length-prefixed JDWP packet.

        Reply packets (flags & 0x80): id(4) + flags(1) + errorCode(2) + data
        Command/event packets:        id(4) + flags(1) + cmdSet(1) + cmd(1) + data
        """
        packet = b""
        while len(packet) < 11:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("closed during header")
            packet += chunk
            if len(packet) < 11:
                continue
            length = struct.unpack(">I", packet[:4])[0]
            total_needed = 4 + length
            while len(packet) < total_needed:
                chunk = self.sock.recv(4096)
                if not chunk:
                    raise ConnectionError("closed during body")
                packet += chunk
            packet_id = struct.unpack(">I", packet[4:8])[0]
            flags = packet[8]
            if flags & 0x80:
                # Reply: errorCode at [9:11]
                error_code = struct.unpack(">H", packet[9:11])[0]
                cmd_set = 0
                cmd = 0
                payload = packet[11:total_needed]
            else:
                # Command/event: cmdSet, cmd at [9:11]
                cmd_set = packet[9]
                cmd = packet[10]
                error_code = 0
                payload = packet[11:total_needed]
            return {
                "length": length,
                "id": packet_id,
                "flags": flags,
                "cmd_set": cmd_set,
                "cmd": cmd,
                "error_code": error_code,
                "payload": payload,
            }

    def _send_packet(self, cmd_set, cmd, payload):
        packet_id = self.next_id
        self.next_id += 1
        # JDWP packet layout:
        #   u4 length (bytes after this field, including id+flags+cmd)
        #   u4 id
        #   u1 flags (0x00 for command packets; server replies with 0x80)
        #   u1 cmd_set
        #   u1 cmd
        #   ... payload ...
        flags = 0x00
        body = struct.pack(">IBBB", packet_id, flags, cmd_set, cmd) + payload
        length = 4 + len(body)
        packet = struct.pack(">I", length) + body
        self.sock.sendall(packet)
        import sys as _s
        _s.stderr.write(f"[TX id={packet_id}] cmdset={cmd_set} cmd={cmd} len={length}\n")

    def _recv_reply(self, expected_id):
        """Receive packets until we get the reply for expected_id."""
        while True:
            pkt = self._recv_packet()
            if pkt["cmd_set"] == 64 and pkt["cmd"] == 100:
                # Event packet — discard; we may receive many during resume
                continue
            if pkt["id"] != expected_id:
                print(f"unexpected id: {pkt['id']} != {expected_id}", file=sys.stderr)
                continue
            return pkt

    def send_cmd(self, cmd_set, cmd, payload):
        """Send a command and wait for its reply."""
        self._send_packet(cmd_set, cmd, payload)
        expected_id = self.next_id - 1
        while True:
            pkt = self._recv_packet()
            if pkt["cmd_set"] == 64 and pkt["cmd"] == 100:
                continue
            if pkt["id"] == expected_id:
                return pkt

    def resume(self):
        """Resume the VM and return the next event packet (block until hit)."""
        # VirtualMachine.Resume = (1/9), no payload
        self._send_packet(1, 9, b"")
        # Now block reading until we get a composite event
        while True:
            pkt = self._recv_packet()
            if pkt["cmd_set"] == 64 and pkt["cmd"] == 100:
                return pkt

    def set_breakpoint_method(self, ref_type_id, method_id):
        """EventRequest.Set with kind=2 (BREAKPOINT), suspendPolicy=1 (ALL)."""
        # EventRequest.Set = (15/1)
        # payload: kind(1) suspendPolicy(1) modifiers_count(4)
        #   modifier: modKind(1) ... data
        # For BREAKPOINT: modKind=4 (LocationOnly), location:
        #   typeTag(1) classID(refTypeId=8) methodID(methodID=8) index(8)
        location = struct.pack(
            ">BQQI",
            1,                # typeTag = class
            ref_type_id,      # classID
            method_id,        # methodID
            0,                # index = 0 (start of method)
        )
        payload = struct.pack(">BBI", 2, 1, 1) + struct.pack(">B", 4) + location
        return self.send_cmd(15, 1, payload)

    def get_ref_type_id(self, signature):
        """VirtualMachine.ClassesBySignature = (1/3), returns one or more refTypeIDs."""
        sig_bytes = signature.encode("ascii")
        payload = struct.pack(">I", len(sig_bytes)) + sig_bytes
        pkt = self.send_cmd(1, 3, payload)
        # reply: classes(count=u4) each: refTypeID(u8) refTypeTag(u1) status(u4)
        if pkt["error_code"] != 0:
            return []
        body = pkt["payload"]
        count = struct.unpack(">I", body[:4])[0]
        ids = []
        offset = 4
        for _ in range(count):
            rid = struct.unpack(">Q", pkt["payload"][offset:offset + 8])[0]
            ids.append(rid)
            offset += 8 + 1 + 4
        return ids

    def get_methods(self, ref_type_id):
        """ReferenceType.Methods = (2/5). Returns declared methods."""
        payload = struct.pack(">Q", ref_type_id)
        pkt = self.send_cmd(2, 5, payload)
        # reply: errorCode(u2) then methods(count=u4) each: methodID(u8) name(string) signature(string) modBits(u4)
        if pkt["error_code"] != 0:
            raise RuntimeError(f"ReferenceType.Methods error {pkt['error_code']}")
        body = pkt["payload"]
        count = struct.unpack(">I", body[:4])[0]
        methods = []
        offset = 4
        for _ in range(count):
            mid = struct.unpack(">Q", body[offset:offset + 8])[0]
            offset += 8
            name_len = struct.unpack(">I", body[offset:offset + 4])[0]
            offset += 4
            name = body[offset:offset + name_len].decode("ascii")
            offset += name_len
            sig_len = struct.unpack(">I", body[offset:offset + 4])[0]
            offset += 4
            sig = body[offset:offset + sig_len].decode("ascii")
            offset += sig_len
            offset += 4  # modBits
            methods.append((mid, name, sig))
        return methods

    def get_fields(self, ref_type_id):
        """ReferenceType.Fields = (2/4). Returns declared fields."""
        payload = struct.pack(">Q", ref_type_id)
        pkt = self.send_cmd(2, 4, payload)
        if pkt["error_code"] != 0:
            raise RuntimeError(f"ReferenceType.Fields error {pkt['error_code']}")
        body = pkt["payload"]
        count = struct.unpack(">I", body[:4])[0]
        fields = {}
        offset = 4
        for _ in range(count):
            fid = struct.unpack(">Q", pkt["payload"][offset:offset + 8])[0]
            offset += 8
            name_len = struct.unpack(">I", pkt["payload"][offset:offset + 4])[0]
            offset += 4
            name = pkt["payload"][offset:offset + name_len].decode("ascii")
            offset += name_len
            sig_len = struct.unpack(">I", pkt["payload"][offset:offset + 4])[0]
            offset += 4
            sig = pkt["payload"][offset:offset + sig_len].decode("ascii")
            offset += sig_len
            offset += 4  # modBits
            fields[name] = (fid, sig)
        return fields


def main():
    if len(sys.argv) < 3:
        print("Usage: jdwp_probe.py <host> <port> <ClassName.methodName>")
        sys.exit(1)
    host, port = sys.argv[1], int(sys.argv[2])
    target = sys.argv[3]  # e.g. "GuideSubshellKt.saveEditorGuide"

    client = JDWPClient(host, port)
    print("handshake OK")

    class_name, method_name = target.split(".")
    # Try several JDWP signature forms until one resolves. Kotlin top-level
    # functions can land in package-private classes with various names.
    candidates = [
        f"LShell/{class_name};",
        f"Lcom/example/tpipewriter/{class_name};",
        f"L{class_name};",
    ]
    print(f"resolving class for {class_name}")
    rids = None
    matched_sig = None
    for sig in candidates:
        print(f"  trying {sig}")
        try:
            ids = client.get_ref_type_id(sig)
        except Exception as e:
            print(f"    error: {e}")
            ids = []
        if ids:
            rids = ids
            matched_sig = sig
            break
    if not rids:
        print(f"FAILED: cannot find class for {class_name}")
        sys.exit(2)

    ref_type_id = rids[0]
    print(f"ref_type_id={ref_type_id}")

    methods = client.get_methods(ref_type_id)
    print(f"class has {len(methods)} methods; looking for {method_name}")
    target_mid = None
    for mid, name, sig in methods:
        if name == method_name:
            target_mid = mid
            print(f"  FOUND: {name} {sig} mid={mid}")
    if target_mid is None:
        print(f"FAILED: method {method_name} not found")
        sys.exit(3)

    # Set breakpoint
    pkt = client.set_breakpoint_method(ref_type_id, target_mid)
    print(f"set_breakpoint_method reply id={pkt['id']} cmd=({pkt['cmd_set']},{pkt['cmd']})")

    print("resuming VM; waiting for breakpoint hit...")
    evt = client.resume()
    print(f"EVENT id={evt['id']} cmd=({evt['cmd_set']},{evt['cmd']}) payload_len={len(evt['payload'])}")

    # Parse the event: cmd=64 cmd=100
    # payload: suspendPolicy(1) events(count=4) events...
    # event: eventKind(1) requestID(4) threadID(refTypeId=threadID=8) location...
    # For breakpoint (kind 2): eventKind=2, requestID, threadID, location
    # location: typeTag(1) classID(8) methodID(8) index(8)
    payload = evt["payload"]
    suspend_policy = payload[0]
    event_count = struct.unpack(">I", payload[1:5])[0]
    print(f"suspend_policy={suspend_policy} event_count={event_count}")
    offset = 5
    for _ in range(event_count):
        kind = payload[offset]
        offset += 1
        req_id = struct.unpack(">I", payload[offset:offset + 4])[0]
        offset += 4
        thread_id = struct.unpack(">Q", payload[offset:offset + 8])[0]
        offset += 8
        # location
        type_tag = payload[offset]; offset += 1
        class_id = struct.unpack(">Q", payload[offset:offset + 8])[0]; offset += 8
        method_id = struct.unpack(">Q", payload[offset:offset + 8])[0]; offset += 8
        index = struct.unpack(">Q", payload[offset:offset + 8])[0]; offset += 8
        print(f"  event: kind={kind} reqID={req_id} thread={thread_id} class={class_id} method={method_id} idx={index}")
        if kind == 2:
            print("  >>> BREAKPOINT HIT <<<")

    print("DONE")


if __name__ == "__main__":
    main()