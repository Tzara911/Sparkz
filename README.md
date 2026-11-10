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

## Local Development Setup

The project includes a shared VS Code launch configuration for connecting Spring Boot to the Supabase PostgreSQL database.

### Setup

1. Clone the repository and open the project folder containing `pom.xml` in VS Code.
2. Install the **Extension Pack for Java**.
3. Copy `.env.example` and rename the copy to `.env`.
   - macOS/Linux: `cp .env.example .env`
   - Windows PowerShell: `Copy-Item .env.example .env`
4. Open `.env` and replace `YOUR_DATABASE_PASSWORD` with the database password shared privately by the team.
5. Open **Run and Debug** in VS Code.
6. Select **Run Sparkz** and click the green play button, or press `F5`.
7. Wait for `Started SparkzApplication`.
8. Open [http://localhost:8080/api/hello](http://localhost:8080/api/hello).

The `Run Sparkz` configuration loads the local `.env` file automatically. Each developer only needs to create `.env` once.

Never commit or share `.env`. If the database password changes, update the local `.env` file.
## Match requests

Issue #8 adds sending, incoming/outgoing requests, and accept/decline/cancel actions.
See [local demo, API contract, and Supabase integration notes](docs/match-requests.md).

## Discovery and matching

Issue #7 adds public profile browsing, reciprocal skill recommendations, skill/day
filters, and sending requests through #8. Open `/discovery.html` in the local demo.
The discovery and match-request pages are **temporary test versions** for backend
integration testing. The team's production frontend will replace them and connect
to the existing Java APIs when ready.
See [discovery API, ranking, database contract, and integration notes](docs/discovery.md).
