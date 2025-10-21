When compiling/building this project certain rules must be always followed:

1. Unless you are specifically compiling or running a unit test. Never compile tests with builds. Tests tend to hang
due to some behavior in the aws sdk that prevents credential loads. Even if they did not they would waste money to run
every time the project is compiled. So never build with tests unless you are specifically running a test.