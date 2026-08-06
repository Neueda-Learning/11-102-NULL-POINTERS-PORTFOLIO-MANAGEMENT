# 📈 Portfolio Manager Application

A full-stack financial portfolio management system designed to track, manage, and analyze multi-asset portfolios (Stocks, Bonds, Cash) with real-time valuation, dynamic allocation visualization, and theme support.

---

## 🚀 Key Features

* **Portfolio Tracking & Management:** View net worth, total invested cost, total profit/loss ($ and %), and asset breakdowns in real-time.
* **Full CRUD Operations:** Add new holdings, adjust quantity/purchase price, and delete/sell existing assets.
* **Asset Allocation Visualization:** Interactive charts displaying percentage exposure across Stocks, Bonds, and Cash.
* **Dark / Light Mode:** Built-in client-side theme switcher with system preference detection and persistent preference storage (`localStorage`).
* **RESTful Architecture:** Clear separation of concerns between the backend database API and frontend presentation.

---

## 🛠️ Tech Stack

### **Backend**
* **Framework:** Java Spring Boot
* **Database:** MySQL
* **OR/Data Access:** Spring Data JPA / Hibernate
* **API Documentation:** Swagger / OpenAPI *(Optional/Stretch)*

### **Frontend**
* **Core:** HTML5, CSS3, Modern JavaScript (ES6+)
* **Styling:** CSS Custom Properties (CSS Variables) for dynamic light/dark theme switching
* **Serving:** Spring Boot Static Resource Handler (`/src/main/resources/static/`)

---

## 🐳 Docker Deployment

This repository now includes:

- `Dockerfile` for building and running the Spring Boot app
- `docker-compose.yml` for running app + MySQL together
- `.env.example` for environment variable template
- `.dockerignore` to keep Docker builds smaller

### Quick Start

1. Copy env template and set values:

```powershell
Copy-Item .env.example .env
```

2. Build and start containers:

```powershell
docker compose --env-file .env up -d --build
```

3. Open app:

- `http://localhost:8090`

4. Stop containers:

```powershell
docker compose --env-file .env down
```

### Notes

- MySQL data is persisted in Docker volume `mysql_data`.
- Compose sets datasource host to `mysql` container, so no local DB host changes are needed.
- Set `FINNHUB_API_KEY` in `.env` for live market data.

---

## 🤖 Jenkins CI/CD

`Jenkinsfile` is provided to:

1. validate Docker/Compose toolchain
2. run tests (`./mvnw -q test`)
3. package Spring Boot app
4. build Docker image
5. deploy with `docker compose`
6. run smoke check on app URL

