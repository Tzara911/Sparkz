# Sparkz
A web application that connects users based on the skills they want to teach and learn
## Git Workflow

* Do not push directly to `main`.
* Use a separate branch for each task.
* Open a Pull Request and get one approval before merging.
* Pull the latest changes and test before merging.
* Never commit passwords or API keys.

## Tech Stack
Backend: Java, Spring Boot
Database: Supabase
Frontend: HTML, CSS, Javascript
Authentication: Google account sign-in (OAuth 2.0)
Build Tool: Maven

## Getting Started

### Prerequisites
- **JDK 17+** (check your `pom.xml` for the exact Java version)
- **Maven** (or just use the included `./mvnw` wrapper, no install needed)
- **Git** to clone the repository
- **A Supabase account and project** (free tier works), for the database
- **A Google Cloud project with OAuth 2.0 credentials**, for Google sign-in
- **A modern web browser** (Chrome, Firefox, Edge, etc.)

### Run the app
```bash
./mvnw spring-boot:run
```
The app will start on http://localhost:8080

## Project Structure
sparkz/
├── src/
│   ├── main/
│   │   ├── java/             # Backend source code (controllers, services, models)
│   │   └── resources/
│   │       ├── static/       # Frontend: HTML, CSS, JavaScript
│   │       └── application.properties   # App configuration
│   └── test/                 # Tests
├── .mvn/                     # Maven wrapper files
├── pom.xml                   # Maven dependencies and build config
├── mvnw / mvnw.cmd           # Maven wrapper scripts (Mac/Linux and Windows)
├── .gitignore                # Files excluded from Git
└── README.md                 # Project documentation