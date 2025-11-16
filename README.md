Youth Banking - Supervised Minor Account Safety System

[![Scala](https://img.shields.io/badge/Scala-3.7.2-blue)](https://www.scala-lang.org/) [![SBT](https://img.shields.io/badge/SBT-1.9.0-lightgrey)](https://www.scala-sbt.org/) [![License](https://img.shields.io/badge/License-Educational-green)](#-license)

> **Note:** University project demonstrating advanced banking safety features for minor accounts with guardian supervision.

A **Scala-based banking system** for minors (ages 13–18) with **parental controls**, **AI-driven fraud detection**, and **real-time risk monitoring**, built with **RBI compliance** and safety-first design principles.

---

## 🎯 Project Overview

<details>
<summary>Click to expand</summary>

Youth Banking is a secure platform enabling minors to learn responsible money management while parents retain full oversight. Features include:

* Customizable spending controls
* AI-powered fraud detection
* Automated risk assessment
* Real-time parental notifications

### Key Innovation: Purpose-Based Fund Override

Parents can **tag sent money with specific purposes** (Medical, Education, Travel, Emergency), letting minors exceed normal limits **only at verified merchants**, maintaining RBI no-overdraft compliance while enabling responsible flexibility.

</details>

---

## 🏗️ Architecture

<details>
<summary>Click to expand</summary>

**Modular Design**

**domain/** – Pure immutable models

```
├── Account, Transaction, Alert, Payee
├── AIRisk
├── Automation
├── CoolingOff
├── FraudDetection
└── ParentDashboard
```

**services/** – Business logic layer

```
├── AIRiskEngine
├── AdvancedFraudDetectionService
├── ScheduledAutomationService
├── CoolingOffService
├── MonitoringService
└── Repository
```

**controllers/** – API/UI controllers

```
└── ParentDashboardController
```

</details>

---

## 🛡️ Core Safety Features

<details>
<summary>Click to expand</summary>

### 1. Customizable Spending Controls

* Monthly Transaction Limit
* Per-Category Limits (Food, Travel, Education, Entertainment)
* Per-Transaction Limit
* Withdrawal Controls (daily, weekly, monthly)

```scala
val limits = Limits(
  monthly = Some(BigDecimal(10000)),
  perTransaction = Some(BigDecimal(2000)),
  perCategory = Map(
    Category.Food -> BigDecimal(3000),
    Category.Entertainment -> BigDecimal(1000)
  ),
  withdrawalLimits = WithdrawalLimits(
    daily = Some(BigDecimal(500)),
    weekly = Some(BigDecimal(2000)),
    monthly = Some(BigDecimal(5000))
  )
)
```

### 2. Smart Incoming Credit Restrictions

* Dynamic limit formula `(Monthly Limit + Monthly Withdrawal Limit) × 2`
* Excess funds locked until parent approval
* Instant parent notifications

### 3. Payee Safety System

* Normal Hours (7 AM–9 PM): free addition, first txn ≤ ₹1,000, subsequent approval required
* Night Hours (9 PM–7 AM): max 1 new payee, first txn ≤ ₹1,000, no additional transfers

### 4. Withdrawal Controls

* Daily/weekly/monthly caps
* Exceeding triggers **one-tap approval**

### 5. Smart Alerts & Monitoring

* Large/unusual txn alerts
* New payee notifications
* Repeated unknown payee payments
* High-risk merchant detection
* Night-time txn monitoring
* AI behavioral pattern analysis

### 6. Unknown Account Transfer Monitoring

* First transfer ≤ ₹1,000 allowed
* Repeated triggers approval + AI risk assessment
* Post-approval payee becomes trusted

### 7. One-Tap Emergency Pass

```scala
emergencyService.activateOverride(
  accountId = "acc123",
  durationMinutes = 60,
  increasedLimit = BigDecimal(5000),
  reason = "Medical emergency"
)
```

### 8. Purpose-Based Override ⭐

* Funds tagged as Medical, Education, Travel, Emergency
* Minors can exceed limits **only at tagged merchants**

```scala
purposeService.sendPurposeFunds(
  accountId = "acc123",
  amount = BigDecimal(5000),
  purpose = Purpose.Medical,
  validUntil = Instant.now().plus(Duration.ofDays(30))
)
```

### 9. Smart Defaults & Scheduled Modes

* School Hours Safe Mode, Max Security Mode, Weekend Explorer Mode
* Automated scheduling with triggers
* Cooling-off system reduces limits after unusual activity

### 10. Fraud & Scam Guardrails

* Blocks multiple new payees at night
* Detects rapid payments to unknown payees
* AI-powered pattern detection & anomaly scoring

</details>

---

## 🤖 Advanced AI/ML Features

<details>
<summary>Click to expand</summary>

* **AI Risk Scoring Engine** – real-time transaction risk
* **Behavioral Baseline Learning** – normal spending patterns
* **Pattern Detection** – account takeover, social engineering
* **Advanced Fraud Detection** – 6 parallel algorithms

</details>

---

## 👨‍👩‍👧 Parent Dashboard Features

<details>
<summary>Click to expand</summary>

* Real-time account overview
* Pending approvals queue
* Batch transaction approval
* Trusted payee management
* Emergency freeze/unfreeze
* Complete audit trails

</details>

---

## 🚀 Getting Started

<details>
<summary>Click to expand</summary>

### Prerequisites

* Scala 3.7.2+
* SBT (Scala Build Tool)
* JDK 11+

### Run Project

```bash
git clone https://github.com/kv-techie/Youth-Banking.git
cd Youth-Banking
sbt compile
sbt test
sbt "runMain examples.AIRiskEngineDemo"
```

### Example Usage

```scala
val account = Account(
  minorId = "minor123",
  balance = BigDecimal(10000),
  limits = Limits(monthly = Some(BigDecimal(5000)), perTransaction = Some(BigDecimal(1000)))
)

val result = transactionController.processTransaction(
  accountId = account.id,
  tx = Transaction(fromAccountId = account.id, toPayeeId = Some("payee1"), amount = BigDecimal(500), category = Category.Food)
)

val dashboard = parentDashboard.getDashboard(parentId = "parent123")
```

</details>

---

## 📂 Project Structure

```
Youth-Banking/
├── src/main/scala/
│   ├── domain/
│   ├── services/
│   ├── controllers/
│   └── examples/
└── build.sbt
```

---

## 🎓 Academic Context

<details>
<summary>Click to expand</summary>

* Advanced Software Design (modular, scalable)
* Domain-Driven Design
* AI/ML Integration
* Fintech safety compliance (RBI guidelines)

**Learning Outcomes:**

* Safety-critical financial systems
* AI-driven fraud detection
* Parent-child supervision workflows
* Statistical anomaly detection
* Modular, maintainable codebases

</details>

---

## 🔒 Compliance & Safety

**RBI Guidelines:**

* ✅ No overdraft
* ✅ Parental consent
* ✅ Transaction limits
* ✅ KYC support
* ✅ Age-appropriate restrictions

**DPDPA 2023 Privacy:**

* Parental consent verification
* Data minimization
* No behavioral ads
* Explicit retention policies

---

## 🤝 Contributing

1. Fork repo
2. Create feature branch
3. Commit changes
4. Push branch
5. Open Pull Request

---

## 📝 License

Educational purposes only.

---

## 👨‍💻 Author

**Kedhar Vinod** – [@kv-techie](https://github.com/kv-techie)

---

## 🙏 Acknowledgments

* RBI guidelines
* DPDPA 2023
* Scala community
* University faculty guidance

---

## 📧 Contact

* GitHub Issues: [Youth-Banking Issues](https://github.com/kv-techie/Youth-Banking/issues)
* Email: [vinod.kedhar05@gmail.com](mailto:vinod.kedhar05@gmail.com)

---

**⭐ If you find this project interesting, please consider starring the repository!**

---
