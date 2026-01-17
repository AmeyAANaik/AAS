# Git Integration Guide - AAS to ERPNext Repository

## Current Setup
- **Current Repo**: https://github.com/AmeyAANaik/AAS
- **Target Repo**: https://github.com/AmeyAANaik/erpnext

## Option 1: Add ERPNext Repo as Git Submodule (Recommended)

This keeps both repositories separate but linked.

```bash
# In your ERPNext repository, add AAS as a submodule
cd /path/to/erpnext
git submodule add https://github.com/AmeyAANaik/AAS aas-service
git commit -m "Add AAS authentication service as submodule"
git push
```

## Option 2: Copy AAS Content to ERPNext Repo

This merges the AAS code into your ERPNext repository.

```bash
# First, commit all AAS changes
cd /workspaces/AAS
git add .
git commit -m "Add authentication service with ERPNext integration"
git push origin main

# Clone ERPNext repo
cd /tmp
git clone https://github.com/AmeyAANaik/erpnext.git
cd erpnext

# Add AAS as a remote and merge
git remote add aas-repo https://github.com/AmeyAANaik/AAS
git fetch aas-repo
git merge aas-repo/main --allow-unrelated-histories

# Resolve any conflicts and push
git push origin develop
```

## Option 3: Create a New Branch in ERPNext for AAS

```bash
# Clone ERPNext
git clone https://github.com/AmeyAANaik/erpnext.git
cd erpnext

# Create a new branch for AAS integration
git checkout -b feature/aas-authentication

# Copy AAS files
cp -r /workspaces/AAS/mw-service ./
cp /workspaces/AAS/*.md ./
cp /workspaces/AAS/STARTUP_COMMANDS.md ./

# Commit and push
git add .
git commit -m "Add AAS authentication service integration"
git push origin feature/aas-authentication
```

## Option 4: Keep Separate and Link via Documentation

Keep both repos separate and just link them in documentation.

### In ERPNext README.md:
```markdown
## Related Repositories
- [AAS Authentication Service](https://github.com/AmeyAANaik/AAS) - Authentication layer with SQLite persistence
```

## Current Status - What to Do Now

### Step 1: Commit Current AAS Changes

```bash
cd /workspaces/AAS

# Add all files
git add .

# Commit with descriptive message
git commit -m "feat: Add authentication service with ERPNext integration

- Created Spring Boot authentication service
- Implemented SQLite persistence layer
- Added ERPNext user synchronization
- Created Docker deployment configuration
- Added comprehensive documentation
- Implemented integration tests

Files created:
- 6 Java source files
- 2 configuration files  
- 2 Docker files
- 1 test file
- 5 documentation files

Completion: 90%"

# Push to AAS repository
git push origin main
```

### Step 2: Choose Your Integration Approach

I recommend **Option 1 (Submodule)** because:
- Keeps code modular
- Both repos can be developed independently
- Easy to update AAS without affecting ERPNext
- Clean separation of concerns

### Step 3: Implement Chosen Option

See commands above for your chosen option.

## Recommended Approach for Your Use Case

Based on your architecture (Authentication Service + ERPNext), I recommend:

```bash
# 1. Commit and push AAS
cd /workspaces/AAS
git add .
git commit -m "feat: Complete authentication service implementation"
git push origin main

# 2. Clone ERPNext repo
cd /tmp
git clone https://github.com/AmeyAANaik/erpnext.git
cd erpnext

# 3. Create integration documentation
cat > AAS_INTEGRATION.md << 'DOC'
# AAS Authentication Service Integration

## Overview
This ERPNext instance integrates with a custom authentication service.

## Repository
https://github.com/AmeyAANaik/AAS

## Setup
1. Clone AAS repository
2. Follow STARTUP_COMMANDS.md
3. Start services with docker-compose

## Architecture
[Diagram showing ERPNext + AAS integration]
DOC

# 4. Commit and push
git add AAS_INTEGRATION.md
git commit -m "docs: Add AAS integration documentation"
git push origin develop
```

