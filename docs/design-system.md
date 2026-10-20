# سیستم طراحی: اپ بیمارستان بین‌المللی ولیعصر تبریز

> وضعیت: تأییدشده توسط مالک پروژه (۲۸ مهر ۱۴۰۵). از ۲۰ اکتبر ۲۰۲۶ رنگ‌های نمونه با **کیت رسمی برند** جایگزین شده‌اند (بخش «تغییر منبع رنگ» را ببینید). پیش‌نمایش: https://claude.ai/artifact/AtK27e6EZmMuxRcz64WHTC

فرض‌ها: اندروید (Kotlin/Compose) + پنل وب؛ کارکنان فقط فارسی RTL؛ مراجعان fa (پیش‌فرض)، az، en، ar، tr با RTL/LTR؛ رنگ اصلی آبی نفتی `#01687F` (برای متن و دکمه)؛ رنگ برند سبز وردانت `#019681`؛ رنگ‌های ثانویه رسمی: بنفش شاهی `#361E58`، زرد شنی `#FFDC6F`.

منشأ رنگ‌ها: کیت رسمی برند (صفحه‌های ۲۶ و ۳۰ کتاب برند). لوگو ترکیبی از سبز وردانت و آبی نفتی است؛ خاکستری ابر (`#939598`) در کیت فقط برای خطوط و متن غیرفعال است و در این پالت متن نمی‌شود.

## ۱. پالت رنگی (حالت روشن)

| نقش | توکن | HEX | کاربرد | رنگ روی آن | کنتراست |
|---|---|---|---|---|---|
| Primary | `primary` | `#01687F` | دکمه، لینک، آیتم انتخاب‌شده (آبی نفتی رسمی) | `#FFFFFF` | 6.39 |
| Primary hover / pressed | `primaryHover` / `primaryPressed` | `#015569` / `#014354` | حالت‌ها | `#FFFFFF` | 8.38 / 10.87 |
| Primary soft | `calm` | `#E0F2F0` | زمینه آیکون، کپسول ناوبری (تیره‌ی سبز وردانت) | `#01687F` | 5.51 |
| Focus ring | `focus` | `#64D8CE` | حلقه ۲ پیکسلی فوکوس | — | — |
| Accent (برند) | `brand` | `#019681` | نوار ۴px زیر هدر، لوگو، نشان «جدید»، آیکون‌های وضعیت. **فقط غیرمتنی؛ هرگز متن، دکمه یا خطا** | — (کنتراست 3.7 با سفید) | 3.70 |
| Brand soft | `brandSoft` | `#E0F2F0` | زمینه نشان برند | — | — |
| Secondary (بنفش شاهی) | `gold` | `#361E58` | متن ثانویه، تأکید بخش بین‌الملل | `#FFFFFF` | 14.16 |
| Secondary deco (زرد شنی) | `goldDeco` | `#FFDC6F` | تزئین و پس‌زمینه نشان‌ها؛ متن روی آن `#1E2A2B` | `#1E2A2B` | 11.06 |
| Cream | `cream` | `#FBF6EC` | کارت‌های گرم | `#1E2A2B` | 13.72 |
| Background | `bg` | `#F6F8F7` | زمینه صفحه | — | — |
| Surface | `surface` | `#FFFFFF` | کارت، هدر، ناوبری | — | — |
| Border | `border` | `#D6DCDB` | حاشیه و جداکننده | — | — |
| Text | `text` | `#1E2A2B` | متن اصلی | روی bg | 13.85 |
| Text secondary | `text2` | `#5B6B6A` | متن فرعی، آیکون پیش‌فرض | روی bg | 5.24 |
| Disabled | `disabled` | `#9AA8A7` | فقط غیرفعال | — | 2.46 |
| Success | `success` / `successSoft` | `#2E7D3E` / `#E7F3EA` | تأیید، انجام شد | `#FFFFFF` | 5.10 |
| Error | `error` / `errorSoft` | `#B9341C` / `#FBECE8` | خطا، حذف (نارنجی‌تر از برند تا اشتباه نشود) | `#FFFFFF` | 5.88 |
| Warning | `warning` / `warningText` / `warningSoft` | `#D08A11` / `#8A4B08` / `#FDF3E3` | هشدار (آیکون / متن / زمینه) | — | 6.18 |
| Info | `info` / `infoSoft` | `#2F6DB5` / `#E8F0FA` | اطلاعات | `#FFFFFF` | 5.28 |

سهم در هر صفحه: خنثی ≈ ۶۰٪، آبی نفتی و سبز وردانت ≈ ۳۰٪، بنفش و زرد ≤ ۱۰٪.

### حالت تیره (شیفت شب کارکنان، و حالت تیره سیستم)

| توکن | HEX | کنتراست |
|---|---|---|
| bg | `#121A1B` | — |
| surface | `#1B2526` | — |
| border | `#2C3A3B` | — |
| text | `#E6EDEC` | 14.87 روی bg |
| text2 | `#A9B7B6` | 7.57 روی surface |
| primary / onPrimary | `#5CC8BC` / `#0F1A1A` | 8.78 |
| calm | `#173130` | — |
| brand | `#4FC3B2` | 8.22 روی bg |
| gold | `#FFDC6F` | 13.22 روی bg |
| success / error / warning / info | `#6CC17E` / `#F08A72` / `#E8B04A` / `#7FB0EA` | همه > 4.5 |

### چرا این رنگ‌ها (خلاصه شناختی)
- آبی نفتی آرام و پاکیزه است و کنتراست متن آن کافی است؛ مسیر اصلی و همه دکمه‌ها با آن است.
- سبز وردانت، رنگ برند رسمی، حس تازگی و سلامت دارد و بدون برانگیختگی قرمز است؛ کنار خطا و هشدار نیست، پس اشتباه گرفته نمی‌شود.
- بنفش شاهی و زرد شنی از کیت رسمی‌اند؛ بنفش تأکید آرام می‌دهد و زرد شنی گرمی بصری. کرم (`cream`) در کیت نیست و عمداً نگه داشته شده است.
- زمینه سفید خالص نیست (خیرگی کمتر)، متن مشکی خالص نیست؛ همه متن‌ها ≥ 4.5:1 برای سالمندان.
- وضعیت‌ها همیشه آیکون + متن دارند (کوررنگی قرمز-سبز).
- حالت تیره کم‌اشباع برای بخش‌های تاریک در شیفت شب.

## ۲. قواعد آیکون
- Material Symbols Outlined (Compose: `Icons.Outlined.*`, جهت‌دارها `Icons.AutoMirrored.Outlined.*`). یک کتابخانه، یک ضخامت.
- 24dp در نوارها و دکمه‌ها؛ 32dp+ برای صفحه خالی. ناحیه لمس ≥ 48dp، فاصله ≥ 8dp.
- رنگ: پیش‌فرض `text2`، فعال `primary`، غیرفعال `disabled`، مخرب `error`.
- آیتم انتخاب‌شده در ناوبری: آیکون Filled + کپسول `calm` 52×28. بقیه Outlined.
- آینه در RTL: بازگشت، chevron لیست، ارسال، نوار پیشرفت. بدون آینه: جستجو، ساعت، تیک، زنگ، کره زمین.
- معنا ثابت: 🔔 فقط اعلان، 🌐 فقط زبان/بین‌الملل، 🛡 فقط گزارش ایمنی بیمار.

## ۳. جایگاه در صفحات کلیدی

### همه صفحات
| منطقه | عنصر | دلیل |
|---|---|---|
| App bar (52dp، surface) | عنوان در ابتدا؛ زبان، جستجو، اعلان در انتها | اطلاعات بالا، اقدام کم‌تکرار بالا |
| زیر app bar | نوار `brand` 4dp | تنها جای ثابت رنگ برند؛ یادآور نمای ساختمان |
| پایین | یک اقدام اصلی (تمام‌عرض 48dp یا FAB 52dp) | منطقه شست |
| Bottom nav (68dp) | ۴ مقصد مراجع / ۵ مقصد کارکنان با برچسب | هیک و میلر |

### ثبت‌نام مراجع
لوگو و نام بالا-وسط → فیلد موبایل (برچسب بیرون، پیش‌شماره جدا، LTR) → راهنما → کارت info «زبان اپ: …» با «تغییر زبان» → فاصله → دکمه «دریافت کد تأیید» تمام‌عرض پایین → لینک متنی «ورود کارکنان با کد پرسنلی».
صفحه کد: ۶ خانه OTP، شمارش معکوس ارسال دوباره، «تأیید» پایین.

### خانه مراجع
App bar: «بیمارستان ولیعصر» + چیپ زبان + 🔔 → کارت hero آبی نفتی با سلام و دکمه «نوبت‌دهی درمانگاه» (به vht-app.ir) → شبکه ۲×۲: بخش‌ها، ساعات ملاقات، مسیر و تماس، پیشنهاد و شکایت → کارت کرم «بیماران بین‌الملل».
برای زبان‌های غیر فارسی کارت بین‌الملل بالای شبکه می‌آید.
Bottom nav: خانه، بخش‌ها، پیگیری، پروفایل.

### خانه کارکنان (فقط فارسی)
App bar: «سلام، [نام]» + 🔍 + 🔔 (نقطه برای خوانده‌نشده) → کارت شیفت هفته → کارت‌های کارتابل با نشان وضعیت (جدید=brand، در انتظار=warning، انجام شد=success) → FAB «گزارش ایمنی» پایین.
Bottom nav: خانه، کارتابل، پیام‌ها، آموزش، پروفایل. «خروج» انتهای پروفایل با رنگ error.

### فرم گزارش ایمنی (چندمرحله‌ای)
بازگشت در ابتدا → «مرحله x از ۳» + نوار پیشرفت → فیلدها (< ۹ در هر مرحله، برچسب بیرون) → خطای inline زیر فیلد → کارت info رمزنگاری/ناشناس → «ادامه» تمام‌عرض پایین.

### پنل وب
سایدبار راست 240px (آیکون + برچسب)، هدر 64px با نوار brand زیر آن، دکمه اصلی کنار عنوان صفحه در سمت چپ، اکشن‌های ردیف با hover.

## ۴. فاصله و تایپوگرافی
- فاصله: 4، 8، 12، 16، 24، 32، 48. حاشیه صفحه 16، padding کارت 12–16، بین کارت‌ها 12.
- گوشه: کارت 14، دکمه/فیلد 12، چیپ 16، FAB 16.
- فونت: Vazirmatn (فارسی، عربی، لاتین با ə ğ ş ı İ برای az/tr؛ پیش از اجرا تست شود).

| توکن | اندازه | وزن |
|---|---|---|
| display | 32 | Bold |
| titleLarge | 26 | Bold |
| title | 18 | Bold |
| body | 16 | Regular (line-height 1.6) |
| label | 14 | Medium |
| caption | 12 | Regular |

## ۵. حالت‌ها
| حالت | دکمه اصلی | فیلد |
|---|---|---|
| Default | `primary` | border `border` |
| Pressed | `primaryPressed` | — |
| Hover (وب) | `primaryHover` | border `text2` |
| Focus | حلقه 2dp `focus` | border `primary` + حلقه |
| Disabled | زمینه `#ECEEEE` متن `disabled` | زمینه `bg` |
| Error | — | border `error` + پیام inline |

## ۶. توکن‌ها

### Jetpack Compose (`ui/theme/Color.kt`)
```kotlin
object ValiasrColors {
    val Primary = Color(0xFF01687F); val PrimaryHover = Color(0xFF015569); val PrimaryPressed = Color(0xFF014354)
    val Calm = Color(0xFFE0F2F0); val Focus = Color(0xFF64D8CE)
    val Brand = Color(0xFF019681); val BrandSoft = Color(0xFFE0F2F0)
    val Gold = Color(0xFF361E58); val GoldDeco = Color(0xFFFFDC6F); val Cream = Color(0xFFFBF6EC)
    val Bg = Color(0xFFF6F8F7); val Surface = Color(0xFFFFFFFF); val Border = Color(0xFFD6DCDB)
    val Text = Color(0xFF1E2A2B); val Text2 = Color(0xFF5B6B6A); val Disabled = Color(0xFF9AA8A7)
    val Success = Color(0xFF2E7D3E); val Error = Color(0xFFB9341C); val Warning = Color(0xFFD08A11)
    val WarningText = Color(0xFF8A4B08); val Info = Color(0xFF2F6DB5)
}

val LightScheme = lightColorScheme(
    primary = ValiasrColors.Primary, onPrimary = Color.White,
    primaryContainer = ValiasrColors.Calm, onPrimaryContainer = ValiasrColors.PrimaryPressed,
    secondary = ValiasrColors.Gold, onSecondary = Color.White,
    secondaryContainer = ValiasrColors.Cream, onSecondaryContainer = Color(0xFF5E4515),
    tertiary = ValiasrColors.Brand, onTertiary = Color.White,
    background = ValiasrColors.Bg, onBackground = ValiasrColors.Text,
    surface = ValiasrColors.Surface, onSurface = ValiasrColors.Text,
    surfaceVariant = Color(0xFFECEEEE), onSurfaceVariant = ValiasrColors.Text2,
    outline = ValiasrColors.Border, error = ValiasrColors.Error, onError = Color.White,
)

val DarkScheme = darkColorScheme(
    primary = Color(0xFF5CC8BC), onPrimary = Color(0xFF0F1A1A),
    primaryContainer = Color(0xFF173130), onPrimaryContainer = Color(0xFF95E4DD),
    secondary = Color(0xFFFFDC6F), onSecondary = Color(0xFF1E1606),
    tertiary = Color(0xFF4FC3B2), onTertiary = Color(0xFF052220),
    background = Color(0xFF121A1B), onBackground = Color(0xFFE6EDEC),
    surface = Color(0xFF1B2526), onSurface = Color(0xFFE6EDEC),
    onSurfaceVariant = Color(0xFFA9B7B6), outline = Color(0xFF2C3A3B),
    error = Color(0xFFF08A72), onError = Color(0xFF2A0D06),
)
```

### CSS (پنل وب `admin_web/app.css`)
```css
:root{
  --primary:#01687F; --primary-hover:#015569; --primary-pressed:#014354; --calm:#E0F2F0; --focus:#64D8CE;
  --brand:#019681; --brand-soft:#E0F2F0; --gold:#361E58; --gold-deco:#FFDC6F; --cream:#FBF6EC;
  --bg:#F6F8F7; --surface:#FFFFFF; --border:#D6DCDB; --text:#1E2A2B; --text2:#5B6B6A; --disabled:#9AA8A7;
  --success:#2E7D3E; --error:#B9341C; --warning:#D08A11; --warning-text:#8A4B08; --info:#2F6DB5;
  --space-1:4px; --space-2:8px; --space-3:16px; --space-4:24px; --space-5:32px; --space-6:48px;
  --radius-card:14px; --radius-control:12px;
}
@media (prefers-color-scheme: dark){:root{
  --primary:#5CC8BC; --calm:#173130; --brand:#4FC3B2; --gold:#FFDC6F; --cream:#2A2418;
  --bg:#121A1B; --surface:#1B2526; --border:#2C3A3B; --text:#E6EDEC; --text2:#A9B7B6;
  --success:#6CC17E; --error:#F08A72; --warning:#E8B04A; --warning-text:#E8B04A; --info:#7FB0EA;
}}
```

## چک‌لیست
- [x] برند ≤ ۳ رنگ (آبی نفتی، سبز وردانت، بنفش شاهی/زرد شنی)؛ یک پالت در کل محصول
- [x] همه جفت متن/زمینه ≥ 4.5:1 (جز disabled که عمداً کم است)
- [x] رنگ‌های وضعیت فقط برای وضعیت؛ خطا قرمز است و رنگ برند سبز است (جدا از هم)
- [x] یک اقدام اصلی در هر صفحه، در منطقه شست
- [x] آیکون یک سبک، لمس ≥ 48dp
- [x] ناوبری ۴ و ۵ آیتم با برچسب
- [x] RTL/LTR و آیکون‌های جهت‌دار
- [x] فاصله ضریب ۸ و مقیاس فونت ثابت
- [x] حالت‌ها تعریف شده؛ برچسب بیرون فیلد و خطای inline

## سؤال‌های باز
- فایل لوگوی رسمی (SVG/PNG) از روابط عمومی: کیت فقط تصویر صفحه دارد و لوگو در اپ هنوز جایگزین نشده است.
- فونت‌های رسمی **Nian** (فارسی) و **TT Drugs** (لاتین): فایل فونت در کیت نیست؛ فعلاً Vazirmatn و فونت سیستم است.
- آیا حالت تیره برای مراجعان هم فعال باشد یا فقط کارکنان؟ (پیشنهاد: هر دو، تابع تنظیم گوشی)

## تغییر منبع رنگ (۲۰ اکتبر ۲۰۲۶)
- منبع رنگ‌ها کیت رسمی برند است (`VALIASR_BRAND_REFERENCE.md`، صفحه‌های ۲۶ و ۳۰).
- قرمز نمونه (`#B3262E`) حذف شد؛ کیت رنگ قرمز ندارد. نوار برند و لوگو حالا سبز وردانت است.
- آبی نفتی `#01687F` جای سبز-آبی قبلی را گرفت، چون سبز وردانت با متن سفید کنتراست ۳.۷ دارد (کمتر از ۴.۵ لازم) و برای دکمه و متن مناسب نیست.
- طلایی نمونه (`#8C6A2B`، `#C9A15A`) جای خود را به بنفش شاهی و زرد شنی رسمی داد.
