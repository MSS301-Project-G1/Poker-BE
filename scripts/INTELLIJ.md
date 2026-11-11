# Run the complete backend from IntelliJ

Open Docker Desktop, then prepare the dependencies from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup-ide.ps1
```

The script stops application containers, starts only their database/Redis/RabbitMQ/
Mailpit/MinIO dependencies, waits for them, and generates 12 local Java 21 Run
configurations. It derives credentials from the resolved Compose configuration
and translates container addresses to the published localhost ports.

In IntelliJ, select **Local - All Services** and click **Run**. This includes
Discovery. The older configurations without the `Local -` prefix do not receive
these environment settings. Gateway uses port 8080, Discovery uses 8761, and
Identity uses 8081. FE should use `VITE_API_BASE_URL=http://localhost:8080`.

Run the setup script again after changing `.env`. The generated configurations
contain local credentials and stay under the ignored `.idea/runConfigurations/`
directory; the diagnostic manifest stays under ignored `.local/`.

Stop the Java processes in IntelliJ before switching back to full Docker mode.
Do not run both copies of the applications on the same ports.
