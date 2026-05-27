# ExpenseFlow - Smart Expense Tracker

A full-stack expense tracking application built with **React 19** (frontend) and **Java Spring Boot** (backend).

## Tech Stack

### Frontend
- **React 19** with Vite 8
- **TailwindCSS 4** for styling
- **Framer Motion** for animations
- **React Router 7** for navigation
- **Lucide React** for icons

### Backend
- **Java 21** with Spring Boot 3.x
- **Spring Data JPA** with PostgreSQL
- **Spring Security** with JWT
- **Maven** for dependency management

## Project Structure

```
Expense-Tracker/
├── frontend/               # React frontend application
│   ├── src/
│   │   ├── components/     # Reusable UI components
│   │   ├── pages/          # Page-level components
│   │   ├── hooks/          # Custom React hooks
│   │   ├── services/       # API service layer
│   │   ├── store/          # State management
│   │   └── utils/          # Utility functions
│   ├── public/
│   └── package.json
├── backend/                # Java Spring Boot backend
│   ├── src/
│   │   ├── main/java/com/expensetracker/
│   │   │   ├── controller/ # REST controllers
│   │   │   ├── service/    # Business logic
│   │   │   ├── repository/ # Data access layer
│   │   │   ├── model/      # Entity models
│   │   │   └── config/     # Configuration
│   │   └── main/resources/
│   └── pom.xml
└── README.md
```

## Getting Started

### Prerequisites
- Node.js 20+
- Java 21+
- PostgreSQL 16+

### Frontend Setup
```bash
cd frontend
npm install
npm run dev
```

### Backend Setup
```bash
cd backend
./mvnw spring-boot:runn
```

## License
MIT
