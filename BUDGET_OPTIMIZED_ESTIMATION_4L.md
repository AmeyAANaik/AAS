# BUDGET OPTIMIZED PROJECT ESTIMATION
## ₹4,00,000 (4 Lakhs)
## Restaurant Multi-Shop Order Management System

---

## EXECUTIVE SUMMARY

**Budget:** ₹4,00,000
**Duration:** 18-20 weeks (4.5-5 months)
**Team:** 1.5 Developers + 1 QA Engineer
**Scope:** MVP (Core Features Only)
**Timeline:** January 2026 - June 2026

---

## BUDGET BREAKDOWN

```
Developer Costs (1.5 FTE × 50 days @ ₹5,000/day):
  Lead Developer:       50 days × ₹7,000/day  = ₹3,50,000
  Junior Developer:     25 days × ₹3,000/day  = ₹75,000
  Subtotal:                                     ₹4,25,000

QA Costs (1 FTE × 30 days @ ₹3,500/day):
  QA Engineer:          30 days × ₹3,500/day  = ₹1,05,000
  Subtotal:                                     ₹1,05,000

Infrastructure (5 months - MINIMAL):
  Server/Hosting:       ₹1,500/month × 5      = ₹7,500
  Database:             ₹1,000/month × 5      = ₹5,000
  Domain & SSL:                                = ₹2,000
  Subtotal:                                     ₹14,500

═══════════════════════════════════════════════════════
TOTAL BUDGET:                                   ₹5,44,500
═══════════════════════════════════════════════════════

❌ BUDGET CONSTRAINT: ₹4,00,000 (61% less than full scope)
```

**NOTE:** Full estimation = ₹23,78,300
Optimized for ₹4,00,000 = 16.8% of full scope

---

## SCOPE REDUCTION STRATEGY

### ✅ INCLUDED (MVP Features)
1. **Authentication & RBAC**
   - Basic login system
   - Role-based access (Admin, Vendor, Shop)
   - No password reset (use admin portal)

2. **Master Data (Basic)**
   - Categories, Items (simple CRUD)
   - Vendors, Shops (simple management)
   - NO Helpers module initially

3. **Order Management (Core)**
   - Order creation (Admin & Shop)
   - Basic order status (NEW, CONFIRMED, DELIVERED)
   - Simple listing (no advanced filters)
   - NO manual vendor assignment (auto-assign only)

4. **Invoicing (Manual)**
   - Simple invoice PDF generation
   - NO auto-invoice generation
   - NO tax calculations

5. **Admin & Shop Panels**
   - Admin: Orders, Vendors, Shops, basic reports
   - Shop: Place orders, track status
   - NO Vendor or Helper panels

6. **Basic Reporting**
   - Simple order list export (CSV)
   - NO analytics, NO Metabase
   - NO background jobs

### ❌ EXCLUDED (Phase 2)
- Helper delivery management
- Payments & ledger system
- Advanced vendor assignment
- Auto-invoice generation
- Monthly billing automation
- Analytics dashboard
- Background jobs
- Email notifications

---

## TEAM COMPOSITION FOR ₹4,00,000

### Lead Developer (1 FTE)
- 50 development days over 20 weeks
- Backend architecture
- Critical API development
- Code review
- Part-time: 2.5 days/week

### Junior Developer (0.5 FTE)
- 25 development days over 20 weeks
- React UI development
- Testing support
- Documentation
- Part-time: 1 day/week

### QA Engineer (1 FTE)
- 30 testing days over 20 weeks
- API testing
- UI testing
- UAT
- Part-time: 1.5 days/week

---

## TIMELINE: 18-20 WEEKS (MVP SCOPE)

### Week 1-2: Setup & Auth (10 days dev, 3 days QA)
- Spring Boot setup
- Database design (simplified)
- Basic JWT authentication
- Role-based access control

### Week 3-4: Master Data (12 days dev, 4 days QA)
- Categories, Items (simple CRUD)
- Vendors, Shops (basic management)
- Simple validation only

### Week 5-7: Order Management (20 days dev, 8 days QA)
- Order creation APIs
- Auto-vendor assignment (simple logic)
- Order status workflow (3 states)
- Basic order listing

### Week 8-9: Invoicing (10 days dev, 3 days QA)
- Manual invoice PDF generation
- Invoice listing
- NO auto-generation

### Week 10-14: Frontend Development (15 days dev, 8 days QA)
- Admin panel (orders, masters, reports)
- Shop panel (order placement, tracking)
- Basic responsive design
- NO vendor/helper panels

### Week 15-16: CSV Export & Reporting (5 days dev, 2 days QA)
- Simple CSV export for orders
- Basic admin report
- NO advanced analytics

### Week 17-20: Testing & Deployment (8 days dev, 5 days QA)
- Integration testing
- Bug fixes
- UAT
- Deployment

---

## TECHNOLOGY STACK (SIMPLIFIED)

**Backend:**
- Java Spring Boot 3.x
- Spring Security (JWT)
- Spring Data JPA
- PostgreSQL (local/shared)
- iText (PDF only, NO Excel)

**Frontend:**
- React (React Hook or minimal state)
- Material UI (basic components)
- NO advanced charting/analytics
- Basic responsive design

**Infrastructure:**
- Docker (single container)
- PostgreSQL (shared instance)
- Basic CI/CD (GitHub Actions minimal)
- NO Metabase/Analytics
- NO background jobs

---

## COST PER COMPONENT

| Component | Dev Days | Dev Cost | QA Days | QA Cost | Total |
|-----------|----------|----------|---------|---------|-------|
| Auth | 4 | ₹20,000 | 2 | ₹7,000 | ₹27,000 |
| Masters | 8 | ₹40,000 | 3 | ₹10,500 | ₹50,500 |
| Orders | 18 | ₹90,000 | 7 | ₹24,500 | ₹1,14,500 |
| Invoices | 8 | ₹40,000 | 2 | ₹7,000 | ₹47,000 |
| Frontend | 16 | ₹80,000 | 8 | ₹28,000 | ₹1,08,000 |
| Testing & Deploy | 6 | ₹30,000 | 8 | ₹28,000 | ₹58,000 |
| **TOTAL** | **60** | **₹3,00,000** | **30** | **₹1,05,000** | **₹4,05,000** |

---

## WHAT YOU GET (MVP)

✅ Working backend with core APIs
✅ Admin panel (orders, masters management)
✅ Shop panel (order placement & tracking)
✅ Basic authentication & roles
✅ Manual invoice generation (PDF)
✅ Simple CSV export
✅ 1 month maintenance support
✅ GitHub repository with documentation

---

## WHAT YOU DON'T GET (Phase 2)

❌ Helper/Delivery management
❌ Payments & ledger system
❌ Vendor portal
❌ Helper portal
❌ Analytics dashboard
❌ Auto-invoice generation
❌ Advanced filtering & search
❌ Email notifications
❌ Background jobs
❌ Advanced reporting
❌ Metabase integration

---

## REALISTIC DELIVERY

**Phase 1 (MVP): 18-20 weeks @ ₹4,00,000**
- Core features only
- Basic UI
- Functional system
- NOT production-ready for 1000+ orders

**Recommended: Phase 2 @ ₹3,00,000 additional**
- Add payments & ledgers
- Add Helper portal
- Add Vendor portal
- Add analytics
- Performance optimization

---

## RISKS & LIMITATIONS

⚠️ **Limited Testing:** Only 30 QA days (vs 79 recommended)
⚠️ **No Analytics:** NO dashboards, NO Metabase
⚠️ **No Automation:** NO background jobs, NO email
⚠️ **Basic UI:** Minimal design, basic responsiveness
⚠️ **Single Developer:** Risk if lead developer unavailable
⚠️ **No Payment Processing:** Won't handle payments
⚠️ **Scalability:** Not optimized for 1000+ transactions
⚠️ **2 Missing Roles:** No Vendor or Helper panels

---

## RECOMMENDATIONS

1. **Start with MVP (₹4,00,000)** - Get basic system working
2. **Plan Phase 2 (₹3,00,000)** - Add missing features after 3 months
3. **Total 2-phase investment:** ₹7,00,000 (~29% of full scope)
4. **Phased approach** ensures ROI faster

---

## PAYMENT TERMS

- **30% upfront:** ₹1,20,000 (project start)
- **40% at Week 10:** ₹1,60,000 (frontend done)
- **30% at delivery:** ₹1,20,000 (UAT passed)

---

## SUCCESS CRITERIA (MVP)

✅ Basic system is working
✅ Admin can manage orders & masters
✅ Shops can place & track orders
✅ Invoices can be generated manually
✅ No critical bugs
✅ Basic documentation
✅ Simple CSV export works

---

## HONEST ASSESSMENT

**This MVP is:**
- ✅ Functional for small-scale use (50-200 orders/month)
- ✅ Good starting point for expansion
- ✅ Achievable with 1.5 devs in 20 weeks
- ✅ Low-risk MVP approach

**This MVP is NOT:**
- ❌ Enterprise-ready
- ❌ Scalable for 1000+ orders
- ❌ Feature-complete as per SRS
- ❌ Dashboard/analytics capable
- ❌ Production-ready without Phase 2

---

**Recommended Approach:**
1. Build MVP with ₹4,00,000
2. Use for 3 months to validate
3. Invest Phase 2 (₹3,00,000) for full system
4. Total investment: ₹7,00,000 (29% of full scope)
5. Get full system in 10 months

---

**Updated:** January 1, 2026
**Status:** MVP Design Complete
**Next Step:** Client approval for phased approach
