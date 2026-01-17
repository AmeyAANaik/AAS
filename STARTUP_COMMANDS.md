# 🚀 Startup Commands - AAS System

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [ERPNext Module Startup](#erpnext-module-startup)
3. [Authentication Service Startup](#authentication-service-startup)
4. [Database Setup](#database-setup)
5. [Quick Start Scripts](#quick-start-scripts)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements
```bash
# Check Python version (3.8+)
python3 --version

# Check Node.js version (14+)
node --version

# Check Docker (if using containerized deployment)
docker --version
docker-compose --version
```

### Install Dependencies
```bash
# Install Python dependencies
pip3 install -r requirements.txt

# Install Node.js dependencies (if frontend exists)
npm install
```

---

## ERPNext Module Startup

### Option 1: Docker Deployment (Recommended)

```bash
# Navigate to ERPNext submodule
cd erpnext

# Start ERPNext with Docker Compose
docker-compose up -d

# Check running containers
docker-compose ps

# View logs
docker-compose logs -f

# Access ERPNext
# Default URL: http://localhost:8000
# Default credentials: Administrator / admin
```

### Option 2: Bench Development Server

```bash
# Navigate to ERPNext directory
cd erpnext

# Initialize bench (first time only)
bench init frappe-bench --frappe-branch version-15
cd frappe-bench

# Create new site
bench new-site aas.localhost

# Install ERPNext app
bench get-app erpnext
bench --site aas.localhost install-app erpnext

# Start development server
bench start

# Access ERPNext at: http://aas.localhost:8000
```

### Option 3: Production Server

```bash
# Navigate to bench directory
cd erpnext/frappe-bench

# Start production server
bench setup production <your-user>

# Enable site
bench use aas.localhost

# Start services
sudo systemctl start nginx
sudo systemctl start supervisor

# Check status
sudo systemctl status nginx
sudo systemctl status supervisor
```

---

## Authentication Service Startup

### Development Mode

```bash
# Navigate to project root
cd /workspaces/AAS

# Start Spring Boot application
./mvnw spring-boot:run

# OR using Java directly
./mvnw clean package
java -jar target/aas-authentication-0.0.1-SNAPSHOT.jar

# Service will be available at: http://localhost:8080
```

### Production Mode

```bash
# Build production JAR
./mvnw clean package -DskipTests

# Run with production profile
java -jar target/aas-authentication-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8080

# Or use Docker
docker build -t aas-auth .
docker run -d -p 8080:8080 --name aas-auth aas-auth
```

---

## Database Setup

### SQLite Database (Default)

```bash
# Database is auto-created at: data/aas_auth.db
# No manual setup required

# To reset database (WARNING: Deletes all data)
rm -f data/aas_auth.db

# Restart application to recreate
./mvnw spring-boot:run
```

### PostgreSQL (Production)

```bash
# Create database
psql -U postgres
CREATE DATABASE aas_production;
CREATE USER aas_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE aas_production TO aas_user;
\q

# Update application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aas_production
spring.datasource.username=aas_user
spring.datasource.password=your_password
```

---

## Quick Start Scripts

### Complete System Startup

Create a file: `start-all.sh`

```bash
#!/bin/bash

echo "🚀 Starting AAS System..."

# Start ERPNext (Docker)
echo "📦 Starting ERPNext..."
cd erpnext
docker-compose up -d
cd ..

# Wait for ERPNext to be ready
echo "⏳ Waiting for ERPNext to initialize..."
sleep 30

# Start Authentication Service
echo "🔐 Starting Authentication Service..."
./mvnw spring-boot:run &

# Wait for service to start
sleep 15

echo "✅ All services started!"
echo "📌 ERPNext: http://localhost:8000"
echo "📌 Auth API: http://localhost:8080"
echo "📌 API Docs: http://localhost:8080/swagger-ui.html"
```

Make it executable:
```bash
chmod +x start-all.sh
./start-all.sh
```

### Stop All Services

Create a file: `stop-all.sh`

```bash
#!/bin/bash

echo "🛑 Stopping AAS System..."

# Stop Authentication Service
echo "Stopping Authentication Service..."
pkill -f "spring-boot:run"

# Stop ERPNext
echo "Stopping ERPNext..."
cd erpnext
docker-compose down
cd ..

echo "✅ All services stopped!"
```

Make it executable:
```bash
chmod +x stop-all.sh
./stop-all.sh
```

---

## Service Endpoints

### Authentication Service API

```bash
# Health Check
curl http://localhost:8080/actuator/health

# Register User
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "email": "test@example.com",
    "role": "VENDOR"
  }'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'

# Get All Users (Admin only)
curl http://localhost:8080/api/users
```

### ERPNext API

```bash
# Get API key from ERPNext
# Settings > User > API Access

# Example: Get Item List
curl -X GET 'http://localhost:8000/api/resource/Item' \
  -H 'Authorization: token <api_key>:<api_secret>'
```

---

## Development Workflow

### Daily Development Startup

```bash
# 1. Start ERPNext in background
cd erpnext && docker-compose up -d && cd ..

# 2. Start Auth Service in development mode
./mvnw spring-boot:run

# 3. In another terminal, start frontend (when available)
# cd frontend && npm start
```

### Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify

# Run specific test
./mvnw test -Dtest=AuthControllerTest
```

---

## Environment Variables

Create a `.env` file in the project root:

```bash
# Database
DATABASE_URL=jdbc:sqlite:data/aas_auth.db

# Server
SERVER_PORT=8080

# ERPNext
ERPNEXT_URL=http://localhost:8000
ERPNEXT_API_KEY=your_api_key
ERPNEXT_API_SECRET=your_api_secret

# JWT (when implemented)
JWT_SECRET=your_secret_key_here
JWT_EXPIRATION=86400000
```

---

## Troubleshooting

### ERPNext Not Starting

```bash
# Check Docker logs
cd erpnext
docker-compose logs -f

# Reset containers
docker-compose down -v
docker-compose up -d

# Check port conflicts
lsof -i :8000
```

### Authentication Service Issues

```bash
# Check if port 8080 is in use
lsof -i :8080

# Kill existing process
kill -9 $(lsof -t -i:8080)

# Check application logs
tail -f logs/application.log

# Clean and rebuild
./mvnw clean install
```

### Database Issues

```bash
# Check database file
ls -lh data/aas_auth.db

# View database schema
sqlite3 data/aas_auth.db ".schema"

# Backup database
cp data/aas_auth.db data/aas_auth.db.backup
```

### Common Port Conflicts

```bash
# Check what's using port 8080
lsof -i :8080

# Check what's using port 8000
lsof -i :8000

# Kill process on port
kill -9 $(lsof -t -i:8080)
```

---

## Useful Commands

### Git Submodule Management

```bash
# Update ERPNext submodule to latest
git submodule update --remote --merge

# Clone project with submodules
git clone --recurse-submodules https://github.com/AmeyAANaik/AAS.git

# Initialize submodules in existing clone
git submodule init
git submodule update
```

### System Monitoring

```bash
# Check all running Java processes
jps -l

# Check Docker containers
docker ps -a

# Check system resources
htop

# Check disk space
df -h
```

---

## Next Steps

1. **Configure ERPNext**
   - Create company
   - Set up items and customers
   - Configure APIs

2. **Test Integration**
   - Test authentication endpoints
   - Verify database connections
   - Test ERPNext API calls

3. **Deploy to Production**
   - Set up production database
   - Configure reverse proxy
   - Set up SSL certificates
   - Configure monitoring

---

*Last Updated: January 17, 2025*
