package domain

/** Transaction categories for spending classification and limits */
enum Category:
  case Food
  case Transport       // FIXED: Added this missing category
  case Travel
  case Education
  case Entertainment
  case Shopping
  case Medical
  case Gifts
  case Utilities
  case Savings
  case Investment
  case Insurance
  case Clothing
  case PersonalCare
  case Books
  case Technology
  case Other
  case Crypto          // High-risk category
  case Gambling        // High-risk category
