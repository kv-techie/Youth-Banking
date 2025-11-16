package domain

/** Merchant / transaction categories */
enum Category:
  case Food, Travel, Education, Entertainment, Medical, Utilities, Grocery, Gifts, Crypto, Gambling, Other

/** Purpose tag that a parent may assign to sent money */
enum PurposeTag:
  case Medical, Travel, Emergency, Education, ExamFees, Misc

