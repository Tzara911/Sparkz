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

## Shared frontend layout (Issue #4)

All four pages (`/`, `/browse.html`, `/matches.html`, `/sessions.html`) use `/sparkz-shell.js` and `/sparkz-theme.css` for one shared header, footer, brand theme, and account control. Navigation is Home, Browse, Match requests, Sessions; only the active tab changes. Login stays on the right and becomes a Profile menu after the server verifies authentication. The root page keeps Issue #4's hero banner and three introduction cards; `/sparkz-layout.css` contains only those home content styles. Its Bootstrap stylesheet is separate from the shared navigation styles.

My profile opens the existing profile overview. Account settings and Manage availability remain clearly disabled until their pages are implemented; Onboarding is shown only for an incomplete profile. Login/logout call optional #5 provider hooks and never simulate authentication. See [shared navigation and auth integration](docs/shared-navigation.md) for the provider adapter and browser verification.

## Match requests

Issue #8 adds sending, incoming/outgoing requests, and accept/decline/cancel actions.
See [local demo, API contract, and Supabase integration notes](docs/match-requests.md).

## Availability and discovery

See [availability/discovery API contract and local testing](docs/availability-discovery.md)
for issues #16 and #7, including the #17 frontend integration contract and #8 match-request links.

## Learning session scheduling

See [session APIs and the local two-account demo](docs/learning-sessions.md) for
issue #18: proposing sessions from accepted matches, online/in-person details,
private lists, and accept/decline/cancel permissions.
Open `/sessions.html` or use **Schedule a session** on an accepted match request
to manage sessions through the responsive page.
