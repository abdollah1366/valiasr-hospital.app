"""نوشتن نام اشخاص (پزشکان) با الفبای زبان مراجع به جای ترجمه: «عبدالله براتی» ← «Abdollah Barati».

فارسی مصوت‌های کوتاه را نمی‌نویسد، پس نام‌های رایج از فهرست خوانده می‌شوند و بقیه با قاعده
حدس زده می‌شوند. نتیجه به‌صورت ترجمه ماشینی ذخیره می‌شود و در پنل قابل اصلاح است.
"""

# نام‌ها و نام‌خانوادگی‌های رایج (شکل انگلیسی رایج در گذرنامه)
NAMES = {
    "دکتر": "Dr.",
    "سید": "Seyed",
    "سیده": "Seyedeh",
    "میر": "Mir",
    "عبدالله": "Abdollah",
    "عبدالرضا": "Abdolreza",
    "عبدالحسین": "Abdolhossein",
    "عبدالرحیم": "Abdolrahim",
    "عبدالعلی": "Abdolali",
    "محمد": "Mohammad",
    "محمدرضا": "Mohammadreza",
    "محمدعلی": "Mohammadali",
    "محمدحسین": "Mohammadhossein",
    "محمود": "Mahmoud",
    "احمد": "Ahmad",
    "علی": "Ali",
    "علیرضا": "Alireza",
    "حسن": "Hassan",
    "حسین": "Hossein",
    "رضا": "Reza",
    "مهدی": "Mehdi",
    "امیر": "Amir",
    "امیرحسین": "Amirhossein",
    "جواد": "Javad",
    "حمید": "Hamid",
    "سعید": "Saeed",
    "مجید": "Majid",
    "مسعود": "Masoud",
    "مصطفی": "Mostafa",
    "مرتضی": "Morteza",
    "مجتبی": "Mojtaba",
    "یوسف": "Yousef",
    "یعقوب": "Yaghoub",
    "ابراهیم": "Ebrahim",
    "اسماعیل": "Esmaeil",
    "اکبر": "Akbar",
    "اصغر": "Asghar",
    "بهروز": "Behrouz",
    "بهرام": "Bahram",
    "بابک": "Babak",
    "پرویز": "Parviz",
    "جعفر": "Jafar",
    "جلال": "Jalal",
    "حمیدرضا": "Hamidreza",
    "داود": "Davoud",
    "رحیم": "Rahim",
    "رسول": "Rasoul",
    "سجاد": "Sajjad",
    "صادق": "Sadegh",
    "عباس": "Abbas",
    "عزیز": "Aziz",
    "فرهاد": "Farhad",
    "فرشید": "Farshid",
    "قاسم": "Ghasem",
    "کاظم": "Kazem",
    "کامران": "Kamran",
    "کریم": "Karim",
    "مهران": "Mehran",
    "منصور": "Mansour",
    "ناصر": "Naser",
    "نادر": "Nader",
    "هادی": "Hadi",
    "وحید": "Vahid",
    "یاسر": "Yaser",
    "فاطمه": "Fatemeh",
    "زهرا": "Zahra",
    "مریم": "Maryam",
    "معصومه": "Masoumeh",
    "زینب": "Zeinab",
    "سارا": "Sara",
    "مینا": "Mina",
    "نرگس": "Narges",
    "لیلا": "Leila",
    "الهام": "Elham",
    "شیرین": "Shirin",
    "پروین": "Parvin",
    "سمیرا": "Samira",
    "مهسا": "Mahsa",
    "نازنین": "Nazanin",
    "سمانه": "Samaneh",
    "طاهره": "Tahereh",
    "اعظم": "Azam",
    "رقیه": "Roghayeh",
    "سکینه": "Sakineh",
    "آرزو": "Arezoo",
    "نسرین": "Nasrin",
    "ژاله": "Jaleh",
    "براتی": "Barati",
    "رضایی": "Rezaei",
    "احمدی": "Ahmadi",
    "محمدی": "Mohammadi",
    "حسینی": "Hosseini",
    "کریمی": "Karimi",
    "رحیمی": "Rahimi",
    "موسوی": "Mousavi",
    "جعفری": "Jafari",
    "صادقی": "Sadeghi",
    "حیدری": "Heidari",
    "نوری": "Nouri",
    "قاسمی": "Ghasemi",
    "کاظمی": "Kazemi",
    "عباسی": "Abbasi",
    "علوی": "Alavi",
    "اکبری": "Akbari",
    "تبریزی": "Tabrizi",
    "تهرانی": "Tehrani",
    "نمونه": "Nemouneh",
    "قلی": "Gholi",
    "نوید": "Navid",
    "فرزانه": "Farzaneh",
    "خسروشاهی": "Khosroshahi",
}

# قاعده حروف برای نام‌های ناشناخته
_CONSONANTS = {
    "ب": "b", "پ": "p", "ت": "t", "ث": "s", "ج": "j", "چ": "ch", "ح": "h", "خ": "kh",
    "د": "d", "ذ": "z", "ر": "r", "ز": "z", "ژ": "zh", "س": "s", "ش": "sh", "ص": "s",
    "ض": "z", "ط": "t", "ظ": "z", "غ": "gh", "ف": "f", "ق": "gh", "ک": "k", "ك": "k",
    "گ": "g", "ل": "l", "م": "m", "ن": "n", "ه": "h", "ة": "h",
}  # fmt: skip
_VOWELS = "aeiou"


def _guess(word: str) -> str:
    """حدس تلفظ: الف، واو و یای وسط کلمه مصوت‌اند؛ بین دو صامت پشت‌سرهم «a» گذاشته می‌شود."""
    out = ""
    n = len(word)
    for i, ch in enumerate(word):
        prev = out[-1:] if out else ""
        if ch in "آ":
            out += "a"
        elif ch in "اأإ":
            out += "" if prev == "a" else "a"
        elif ch == "ع":
            out += "a" if i == 0 else ""
        elif ch in "وؤ":
            nxt = word[i + 1] if i + 1 < n else ""
            out += "v" if i == 0 or prev in _VOWELS or nxt in "اآیي" else "ou"
        elif ch in "یيئى":
            out += "y" if i == 0 or prev in _VOWELS and i < n - 1 else "i"
        elif ch == "ه" and i == n - 1 and i > 0:
            out += "eh"
        elif ch in _CONSONANTS:
            latin = _CONSONANTS[ch]
            # دو صامت پشت‌سرهم بعد از صامت اول کلمه: مصوت کوتاه «a» حدس زده می‌شود.
            if prev and prev not in _VOWELS and len(out) <= 2:
                out += "a"
            out += latin
        elif ch.isascii():
            out += ch
    return out


ZWNJ = "\u200c"


def _word(w: str) -> str:
    known = NAMES.get(w)
    if known is None and w.endswith("زاده") and len(w) > 4:
        known = _word(w[:-4]).lower() + "zadeh"
    if known is None and w.endswith("ی") and w[:-1] in NAMES:
        known = NAMES[w[:-1]] + "i"
    return known or _guess(w)


def latin(name: str) -> str:
    """نام فارسی با الفبای لاتین (شکل انگلیسی)."""
    words = []
    for w in name.split():
        # «حسین‌زاده»: هر بخش جدا خوانده و بدون فاصله به هم چسبانده می‌شود.
        parts = (
            [_word(p) for p in w.split(ZWNJ) if p]
            if NAMES.get(w.replace(ZWNJ, "")) is None
            else [NAMES[w.replace(ZWNJ, "")]]
        )
        word = parts[0] + "".join(p.lower() for p in parts[1:])
        words.append(word[:1].upper() + word[1:])
    return " ".join(words)


# الفبای آذربایجانی و ترکی برای همان صداها
_AZ = [("sh", "ş"), ("ch", "ç"), ("kh", "x"), ("gh", "q"), ("zh", "j"), ("j", "c"), ("ou", "u")]
_TR = [("sh", "ş"), ("ch", "ç"), ("kh", "h"), ("gh", "ğ"), ("zh", "j"), ("j", "c"), ("ou", "u")]


def _respell(text: str, rules: list[tuple[str, str]]) -> str:
    words = []
    for w in text.split(" "):
        if w == "Dr.":
            words.append(w)
            continue
        low = w.lower()
        # جای‌گذاری دومرحله‌ای تا خروجی یک قاعده (مثلاً «j» از «zh») دوباره با قاعده دیگری عوض نشود.
        for i, (src, _dst) in enumerate(rules):
            low = low.replace(src, f"\x00{i}\x00")
        for i, (_, dst) in enumerate(rules):
            low = low.replace(f"\x00{i}\x00", dst)
        if low.startswith("ğ"):
            low = "g" + low[1:]  # «ğ» در آغاز کلمه ترکی نمی‌آید
        words.append(low[:1].upper() + low[1:])
    return " ".join(words)


def to_arabic(name: str) -> str:
    """عربی همان خط را دارد؛ فقط «دکتر» کوتاه و ی و ک به شکل عربی نوشته می‌شوند."""
    words = ["د." if w == "دکتر" else w for w in name.split()]
    return " ".join(words).replace("ی", "ي").replace("ک", "ك")


def transliterate(name: str, language: str) -> str:
    if language == "ar":
        return to_arabic(name)
    text = latin(name)
    if language == "az":
        return _respell(text, _AZ)
    if language == "tr":
        return _respell(text, _TR)
    return text
