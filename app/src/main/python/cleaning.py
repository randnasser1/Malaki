import re
import html
from typing import Dict, Optional, List, Union, Iterable
from collections import Counter
import warnings

SLANG_DICT = {
    'asl': 'age sex location',
    'a/s/l': 'age sex location',
    'm/f': 'male or female',
    'f/m': 'female or male',
    's/l': 'sex location',
    'pic': 'picture',
    'pics': 'pictures',
    'photo': 'photo',
    'photos': 'photos',
    'snap': 'snapchat',
    'sc': 'snapchat',
    'kik': 'kik messenger',
    'vid': 'video',
    'vids': 'videos',
    'wyd': 'what you doing',
    'wya': 'where you at',
    'hmu': 'hit me up',
    'irl': 'in real life',
    'j4f': 'just for fun',
    'age': 'age',
    'sex': 'sex',
    'location': 'location',
    'single': 'single',
    'u': 'you',
    'ur': 'your',
    'r': 'are',
    'yr': 'your',
    'yrs': 'yours',
    'plz': 'please',
    'pls': 'please',
    'thx': 'thanks',
    'ty': 'thank you',
    'tyvm': 'thank you very much',
    'np': 'no problem',
    'yw': 'you are welcome',
    'idk': 'i do not know',
    'ik': 'i know',
    'dunno': 'do not know',
    'idc': 'i do not care',
    'tbh': 'to be honest',
    'imo': 'in my opinion',
    'imho': 'in my humble opinion',
    'fwiw': 'for what it is worth',
    'afaik': 'as far as i know',
    'lol': 'laughing out loud',
    'lmao': 'laughing my ass off',
    'lmfao': 'laughing my fucking ass off',
    'rofl': 'rolling on the floor laughing',
    'roflmao': 'rolling on the floor laughing my ass off',
    'lulz': 'laughs',
    'lel': 'laugh',
    'omg': 'oh my god',
    'omfg': 'oh my fucking god',
    'wtf': 'what the fuck',
    'wth': 'what the hell',
    'nvm': 'never mind',
    'jk': 'just kidding',
    'j/k': 'just kidding',
    'smh': 'shaking my head',
    'afk': 'away from keyboard',
    'brb': 'be right back',
    'gtg': 'got to go',
    'ttyl': 'talk to you later',
    'cya': 'see you',
    'fyi': 'for your information',
    'gonna': 'going to',
    'wanna': 'want to',
    'gotta': 'got to',
    'kinda': 'kind of',
    'sorta': 'sort of',
    'lemme': 'let me',
    'gimme': 'give me',
    'outta': 'out of',
    'tryna': 'trying to',
}

SINGLE_LETTER_DICT = {
    'f': 'female',
    'u': 'you',
    'r': 'are',
    'c': 'see',
    'y': 'why',
    'b': 'be',
    'n': 'and',
    'k': 'okay',
}

STATIC_SPELLING_DICT = {
    'teh': 'the',
    'hte': 'the',
    'adn': 'and',
    'nad': 'and',
    'jsut': 'just',
    'siad': 'said',
    'fomr': 'from',
    'waht': 'what',
    'yuo': 'you',
    'thier': 'their',
    'recieve': 'receive',
    'seperate': 'separate',
    'definately': 'definitely',
    'accomodate': 'accommodate',
    'goverment': 'government',
}

# ADDED: Missing KEYBOARD_PATTERNS
KEYBOARD_PATTERNS = {
    re.compile(r'iapos;', re.I): "I'm",
    re.compile(r'youse', re.I): 'use',
    re.compile(r'pare', re.I): 'are',
    re.compile(r'woarek', re.I): 'work',
    re.compile(r'aree', re.I): 'are',
    re.compile(r'byout', re.I): 'but',
    re.compile(r'youre', re.I): 'your',
    re.compile(r'soyounds', re.I): 'sounds',
    re.compile(r'coareareect', re.I): 'correct',
    re.compile(r'\bhte\b', re.I): 'the',
    re.compile(r'\bwich\b', re.I): 'which',
    re.compile(r'\btehre\b', re.I): 'there',
    re.compile(r'\btahts\b', re.I): "that's",
    re.compile(r'hglsdfhglhreuh', re.I): '',
    re.compile(r'sdhfgilhsdflkghlfdghlfsd', re.I): '',
    re.compile(r'sdijfioashfusdghsdfughosdhfsdhg', re.I): '',
    re.compile(r'jaja+', re.I): 'haha',
    re.compile(r'cvbvcv', re.I): '',
    re.compile(r'\bwhcih\b', re.I): 'which',
    re.compile(r'\bna d\b', re.I): 'and',
}

# ADDED: Missing CONTRACTIONS
CONTRACTIONS = {
    re.compile(r"\b(i'm|im)\b", re.I): "I am",
    re.compile(r"\b(i'll|ill)\b", re.I): "I will",
    re.compile(r"\b(i'd|id)\b", re.I): "I would",
    re.compile(r"\b(i've|ive)\b", re.I): "I have",
    re.compile(r"\b(don't|dont)\b", re.I): "do not",
    re.compile(r"\b(doesn't|doesnt)\b", re.I): "does not",
    re.compile(r"\b(won't|wont)\b", re.I): "will not",
    re.compile(r"\b(can't|cant)\b", re.I): "cannot",
    re.compile(r"\b(couldn't|couldnt)\b", re.I): "could not",
    re.compile(r"\b(wouldn't|wouldnt)\b", re.I): "would not",
    re.compile(r"\b(shouldn't|shouldnt)\b", re.I): "should not",
    re.compile(r"\b(wasn't|wasnt)\b", re.I): "was not",
    re.compile(r"\b(weren't|werent)\b", re.I): "were not",
    re.compile(r"\b(haven't|havent)\b", re.I): "have not",
    re.compile(r"\b(hasn't|hasnt)\b", re.I): "has not",
    re.compile(r"\b(hadn't|hadnt)\b", re.I): "had not",
    re.compile(r"\b(didn't|didnt)\b", re.I): "did not",
    re.compile(r"\b(isn't|isnt)\b", re.I): "is not",
    re.compile(r"\b(aren't|arent)\b", re.I): "are not",
    re.compile(r"\b(ain't|aint)\b", re.I): "is not",
    re.compile(r"\b(you're|youre)\b", re.I): "you are",
    re.compile(r"\b(you'll|youll)\b", re.I): "you will",
    re.compile(r"\b(you'd|youd)\b", re.I): "you would",
    re.compile(r"\b(you've|youve)\b", re.I): "you have",
    re.compile(r"\b(he's|hes)\b", re.I): "he is",
    re.compile(r"\b(he'll|hell)\b", re.I): "he will",
    re.compile(r"\b(he'd|hed)\b", re.I): "he would",
    re.compile(r"\b(she's|shes)\b", re.I): "she is",
    re.compile(r"\b(she'll|shell)\b", re.I): "she will",
    re.compile(r"\b(she'd|shed)\b", re.I): "she would",
    re.compile(r"\b(it's|its)\b", re.I): "it is",
    re.compile(r"\b(it'll|itll)\b", re.I): "it will",
    re.compile(r"\b(it'd|itd)\b", re.I): "it would",
    re.compile(r"\b(we're|were)\b", re.I): "we are",
    re.compile(r"\b(we'll|well)\b", re.I): "we will",
    re.compile(r"\b(we'd|wed)\b", re.I): "we would",
    re.compile(r"\b(we've|weve)\b", re.I): "we have",
    re.compile(r"\b(they're|theyre)\b", re.I): "they are",
    re.compile(r"\b(they'll|theyll)\b", re.I): "they will",
    re.compile(r"\b(they'd|theyd)\b", re.I): "they would",
    re.compile(r"\b(they've|theyve)\b", re.I): "they have",
}

"""
Text cleaning functions and dictionaries - NO PANDAS REQUIRED
Runs on Android with Chaquopy without heavy dependencies
"""

# Keep all your regex patterns
URL_RE = re.compile(r'http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+')
WWW_RE = re.compile(r'www\.[a-zA-Z0-9\-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?')
TAG_RE = re.compile(r'<[^>]+>')
MD5_RE = re.compile(r'\b[a-f0-9]{32}\b', re.I)
HEX_RE = re.compile(r'\b[0-9a-f]{8,40}\b', re.I)
MENTION_RE = re.compile(r'@\w+')
AUTHOR_RE = re.compile(r'^\w+:\s*', re.M)
TIME_RE = re.compile(r'\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:am|pm)?\b', re.I)
DATE_RE = re.compile(r'\b\d{4}[-/]\d{1,2}[-/]\d{1,2}\b')
PUNCT_RE = re.compile(r'([!?.]){2,}')
PUNCT_SPACE_RE = re.compile(r'([,;:])\1+')
SPACE_RE = re.compile(r'\s+')
WORD_RE = re.compile(r'\b\w+\b')
SINGLE_LETTER_RE = re.compile(r'\b([a-z])\b(?!\')')


class SpellingCorrector:
    """Simple dictionary-based spelling correction - NO ML DEPENDENCIES"""

    def __init__(self, model_name: str = None, device: Optional[str] = None, batch_size: int = 32):
        self.batch_size = batch_size
        self.device = "cpu"
        print("✓ Using dictionary-based spelling correction (no ML model)")

    def correct(self, texts: Union[str, List[str]]) -> Union[str, List[str]]:
        """Apply dictionary-based corrections to text(s)."""
        single_input = isinstance(texts, str)
        if single_input:
            texts = [texts]

        corrected = []
        for text in texts:
            if not text or not isinstance(text, str):
                corrected.append(text)
                continue

            result = text.lower()

            # Apply static spelling corrections
            for misspelled, correct_word in STATIC_SPELLING_DICT.items():
                pattern = re.compile(r'\b' + re.escape(misspelled) + r'\b')
                result = pattern.sub(correct_word, result)

            # Apply slang dictionary
            for slang, expansion in SLANG_DICT.items():
                pattern = re.compile(r'\b' + re.escape(slang) + r'\b')
                result = pattern.sub(expansion, result)

            corrected.append(result)

        return corrected[0] if single_input else corrected


# Global spelling corrector instance
_SPELLING_CORRECTOR = None

def get_spelling_corrector(force_reload: bool = False) -> SpellingCorrector:
    """Get or create the global spelling corrector instance."""
    global _SPELLING_CORRECTOR
    if _SPELLING_CORRECTOR is None or force_reload:
        _SPELLING_CORRECTOR = SpellingCorrector()
    return _SPELLING_CORRECTOR


def clean_text(text: str,
               aggressive: bool = False,
               slang_dict: Optional[Dict] = None,
               use_spelling_correction: bool = True,
               spelling_batch_size: int = 32) -> str:
    """Main text cleaning function - NO PANDAS NEEDED."""
    if not text or not isinstance(text, str):
        return ""

    # Unescape HTML
    text = html.unescape(text)

    # Lowercase
    text = text.lower()

    # Remove URLs
    text = URL_RE.sub(' ', text)
    text = WWW_RE.sub(' ', text)

    # Remove HTML tags
    text = TAG_RE.sub(' ', text)

    # Remove hashes and hex
    text = MD5_RE.sub(' ', text)
    text = HEX_RE.sub(' ', text)

    # Remove author markers, mentions, time/date
    text = AUTHOR_RE.sub('', text)
    text = MENTION_RE.sub(' ', text)
    text = TIME_RE.sub(' ', text)
    text = DATE_RE.sub(' ', text)

    # Fix common keyboard patterns
    for pattern, replacement in KEYBOARD_PATTERNS.items():
        text = pattern.sub(replacement, text)

    # Apply static spelling corrections
    for misspelled, correct in STATIC_SPELLING_DICT.items():
        pattern = re.compile(r'\b' + re.escape(misspelled) + r'\b')
        text = pattern.sub(correct, text)

    # Handle contractions
    if aggressive:
        for pattern, replacement in CONTRACTIONS.items():
            text = pattern.sub(replacement, text)

    # Apply slang dictionary
    if slang_dict is None:
        slang_dict = SLANG_DICT

    def replace_slang(match):
        word = match.group(0)
        if word in ["im", "id", "ill", "ive"] and aggressive:
            return word
        return slang_dict.get(word.lower(), word)

    text = WORD_RE.sub(replace_slang, text)

    # Handle single letters
    def replace_single_letter(match):
        letter = match.group(1)
        context_before = text[max(0, match.start()-3):match.start()]
        context_after = text[match.end():match.end()+3]

        if any(ctx in context_before + context_after for ctx in ["'", "im", "re", "ve", "ll", "d"]):
            return letter
        return SINGLE_LETTER_DICT.get(letter, letter)

    text = SINGLE_LETTER_RE.sub(replace_single_letter, text)

    # Apply spelling correction if requested
    if use_spelling_correction:
        corrector = get_spelling_corrector()
        text = corrector.correct(text)

    # Normalize repeated letters
    def normalize_repeats(match):
        char = match.group(1)
        return char * 2

    text = re.sub(r'(.)\1{3,}', normalize_repeats, text)

    # Remove excessive punctuation
    text = PUNCT_RE.sub(r'\1', text)
    text = PUNCT_SPACE_RE.sub(r'\1', text)

    # Clean up special characters
    text = re.sub(r'[^a-z0-9\s.,!?;:\'\"()\-]', ' ', text)

    # Fix spacing around punctuation
    text = re.sub(r'\s+([.,!?;:])', r'\1', text)
    text = re.sub(r'([.,!?;:])(?=[^\s])', r'\1 ', text)

    # Normalize whitespace
    text = SPACE_RE.sub(' ', text).strip()

    return text


def clean_text_batch(texts: List[str],
                     aggressive: bool = False,
                     slang_dict: Optional[Dict] = None,
                     use_spelling_correction: bool = True,
                     batch_size: int = 32) -> List[str]:
    """Clean a batch of texts."""
    return [clean_text(t, aggressive, slang_dict, use_spelling_correction) for t in texts]


def clean_text_iterable(texts: Iterable[str],
                        aggressive: bool = False,
                        slang_dict: Optional[Dict] = None,
                        use_spelling_correction: bool = True) -> List[str]:
    """Work with any iterable (list, tuple, set, etc.) - pandas-free alternative."""
    return clean_text_batch(list(texts), aggressive, slang_dict, use_spelling_correction)


def find_unusual_patterns(texts: List[str],
                          known_dict: Optional[Dict] = None,
                          top_k: int = 15) -> Dict:
    """Find patterns not covered by cleaning pipeline."""
    known_dict = known_dict or {**SLANG_DICT, **SINGLE_LETTER_DICT, **STATIC_SPELLING_DICT}
    known_words = set(known_dict.keys()) | set(known_dict.values())
    auto_fixed = {'im', 'dont', 'cant', 'teh', 'hte', 'adn', 'nad'}

    words = []
    for text in texts:
        if text and isinstance(text, str):
            words.extend(re.findall(r'\b[a-z]+\b', text.lower()))

    unknown = [w for w in words if w not in known_words and w not in auto_fixed and len(w) > 2]

    patterns = {
        'excessive_repeats': [w for w in unknown if re.search(r'(.)\1{3,}', w)],
        'moderate_repeats': [w for w in unknown if re.search(r'(.)\1{2}', w)
                             and not re.search(r'(.)\1{3,}', w)],
        'keyboard_mash': [w for w in unknown if re.search(r'(qwe|wer|asd|sdf|zxc)', w)],
        'no_vowels': [w for w in unknown if len(w) > 4 and not re.search(r'[aeiouy]', w)],
    }

    for name, pattern_words in patterns.items():
        if pattern_words:
            print(f"\n{name.upper()}:")
            for word, count in Counter(pattern_words).most_common(top_k):
                print(f"  {word}: {count}")

    return patterns


# ============================================================
# SIMPLE USAGE EXAMPLES (pandas-free)
# ============================================================

if __name__ == "__main__":
    # Example 1: Clean single text
    text = "Hey u, check out https://example.com LOL that's gr8!"
    cleaned = clean_text(text, aggressive=True)
    print(f"Original: {text}")
    print(f"Cleaned:  {cleaned}")

    # Example 2: Clean a list of texts (pandas-free)
    texts = [
        "I'm feeling g8 today!",
        "WYD? HMU later plz",
        "This is teh best thing ever"
    ]
    cleaned_batch = clean_text_batch(texts, aggressive=True)
    for orig, clean in zip(texts, cleaned_batch):
        print(f"\n'{orig}' -> '{clean}'")

    # Example 3: Load from JSON instead of CSV
    import json
    # Instead of pd.read_csv, use:
    with open('data.json', 'r') as f:
        data = json.load(f)  # List of dicts
    texts = [item['text'] for item in data]
    cleaned = clean_text_batch(texts)