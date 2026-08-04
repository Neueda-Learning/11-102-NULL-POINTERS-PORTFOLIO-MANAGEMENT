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
