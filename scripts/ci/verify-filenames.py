# An XML file can contain one or more Sailpoint objects. 
# Each object XML is of the form:
# <ObjectType name="Object Name">...
#
# Typically, only object will be present in each file, with a DOCTYPE above the opening tag.
# 
# Some names are prefixed with e.g, the name of the customer or the developer of a plugin or common library object.
# Developer / customer names are strictly optional. This is a style preference. 

# For example, these are all possible names for a Rule object provided by IDW:
# <Rule name="IDW - Rule Name">...
# <Rule name="IDW - Rule - Rule Name">...
# <Rule name="Rule - IDW Rule Name">...
# <Rule name="IDW Rule Name">...
# <Rule name="Rule Name">... (if the Rule Name is unique enough to not require a prefix... most out-of-box objects are this way)

# The filename for this object ought to be IDW-Rule-RuleName.xml (with optional dashes for the spaces in Rule-Name). 
# # Only letters, numbers, dashes, and underscores should be in the Object Name part of the filename. 
# Spaces should be collapsed or replaced by dashes.

# Here are some other XML object names and their expected filenames:
# <Rule name="Customer - Rule - Ldap Before Provisioning">... -> Customer-Rule-LdapBeforeProvisioning.xml or Rule-CustomerLdapBeforeProvisioning.xml
# <Rule name="Customer - Ldap Before Provisioning Rule">... -> Customer-Rule-LdapBeforeProvisioning.xml or Rule-CustomerLdapBeforeProvisioning.xml
# <Workflow name="Customer - Workflow - Attribute Sync">... -> Customer-Workflow-AttributeSync.xml or Workflow-CustomerAttributeSync.xml
# Somewhere in the directory hierarchy for each file must be a folder matching the object type, e.g., a Workflow object might be in config/Workflow/Workflow-SomeName.xml or config/Workflow/Provisioning/Workflow-SomeName.xml, but not config/Rule/Workflow-SomeName.xml.

# Some files will begin with the XML tag <sailpoint>. These files are not expected to follow the same naming convention as the object XML files, and should be ignored by this script.

# This script will read the XML files in the specific config directory (command line argument) and verify that the
# filenames are as expected. If a filename does not match one of the expected patterns for a given object name, 
# the script will print an error to be picked up by a CI/CD pipeline.

# Usage: python verify-filenames.py --customer <customer_prefix> <config_directory1> <config_directory2> ... 

"""
Verify SailPoint XML config filenames match naming conventions.

This script reads XML files from a config directory and validates that filenames
correspond to the object type and name declared in each file's XML. It enforces
three key checks:

  1. Directory hierarchy: At least one ancestor directory matches the object type.
  2. Object type in filename: The object type appears in the filename stem.
  3. Name words in filename: Significant words from the object name appear in
     the filename (with support for aliases and customizable exclusions).

Errors are categorized as:
  - ERROR: Violations of the naming conventions that must be fixed.
  - WARN: Missing words that are implied by directory names (informational).

Configuration is provided via a JSON file (--config) or command-line flags
(--customer). The script supports type aliases (Workgroup for Identity,
Report/Reports for TaskDefinition), word abbreviations, and directory-level
exclusions.

Exit codes:
  0: All checks passed (or only WARNs found).
  1: One or more ERRORs found.
  2: Configuration or I/O error.
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Optional, Set, Dict

# ──────────────────────────────────────────────────────────────────────────────
# Helpers for filename ↔ object-name comparison
# ──────────────────────────────────────────────────────────────────────────────

# Common English words that appear in SailPoint object names but are never
# meaningful in filenames and should not be required to appear there.
_STOP_WORDS: frozenset = frozenset[str]({
    'a', 'an', 'the',
    'and', 'or', 'nor',
    'by', 'for', 'from', 'in', 'into', 'of', 'on', 'at', 'to', 'with',
    'is', 'are', 'was', 'were',
})

_BOILERPLATE_WORDS: frozenset = frozenset[str]({
    "sailpoint", "sp", "iiq"
})

# Words shorter than this are ignored when checking name→filename correspondence.
_MIN_WORD_LEN = 2

# Type alias: maps normalized full-word → set of normalized abbreviations.
_AliasMap = Dict[str, Set[str]]


def _normalize(s: str) -> str:
    """
    Normalize a string for comparison by lowercasing and removing non-alphanumerics.

    Args:
        s: The string to normalize.

    Returns:
        A normalized (lowercased, non-alphanumeric-free) version of s.
    """
    return re.sub(r'[^a-z0-9]', '', s.lower())


def _load_config(path: str, customer: Optional[str]) -> tuple[_AliasMap, Set[str], Set[str], str]:
    """
    Load a JSON configuration file and return ``(alias_map, exclude_set)``.

    Expected format::

        {
            "aliases": {
                "Affiliates": ["Affs", "Aff"],
                "Employees":  ["Emps", "Emp"]
            },
            "exclude": [
                "Configuration/SystemConfiguration.xml",
                "UIConfig/"
            ]
        }

    Both keys are optional.

    ``alias_map``
        Dict mapping normalized full-word -> set of normalized abbreviations.

    ``ignore_words``
        Set of normalized words that are exempt from the filename check even
        when they appear in the object name.

    ``exclude_set``
        Set of lower-cased path strings (relative to the config directory)
        that should be skipped.  An entry ending with ``/`` is treated as a
        directory prefix; any file whose relative path starts with that prefix
        (case-insensitive) will be excluded.
    """
    with open(path, encoding='utf-8') as fh:
        data = json.load(fh)

    alias_map: _AliasMap = {}
    for full_word, abbrevs in (data.get('aliases') or {}).items():
        norm_full = _normalize(full_word)
        for abbrev in abbrevs:
            alias_map.setdefault(norm_full, set()).add(_normalize(abbrev))

    ignore_words: Set[str] = {_normalize(w) for w in (data.get('ignoreWords') or [])}

    exclude_set: Set[str] = {e.lower() for e in (data.get('exclude') or [])}

    file_customer: str = data.get('customer', '').strip()
    if file_customer and file_customer != customer:
        print(f"WARNING: customer prefix '{file_customer}' in config file does not match "
              f"command-line argument '{customer}'", file=sys.stderr)
        
        customer = file_customer

    return alias_map, ignore_words, exclude_set, (customer or '').strip()


def _significant_words(
    name: str,
    object_type: str,
    customer_prefix: Optional[str],
    object_subtype: Optional[str] = None,
    ignore_words: Optional[Set[str]] = None,
) -> List[str]:
    """
    Return the words from *name* that must appear in the filename.

    Excluded from the result:
    - Any word whose normalized form equals the normalized object type.
    - Any word whose normalized form equals the normalized object subtype
      (the 'type' attribute on the XML element, e.g. "FieldValue" for a Rule).
    - Any word whose normalized form equals a normalized word from *customer_prefix*
      (because the prefix is an optional style choice and may appear differently
      in the filename, e.g. "Penn" in the name vs "UPenn" in the filename).
    - Any word whose normalized form is in *ignore_words*.
    - Common stop words.
    - Words shorter than _MIN_WORD_LEN characters.

    The conventional ' - ' separator between name segments is used to split
    the name before splitting individual segments on spaces.
    """
    segments = re.split(r' - ', name)
    words: List[str] = []
    for segment in segments:
        words.extend(w for w in re.split(r'[\s_/]+', segment) if w)

    norm_type = _normalize(object_type)
    norm_subtype = _normalize(object_subtype) if object_subtype else None
    _ignore = ignore_words or set()
    customer_norms: frozenset = frozenset(
        _normalize(w) for w in (customer_prefix or '').split() if w
    )

    result: List[str] = []
    for word in words:
        norm = _normalize(word)
        if norm == norm_type:
            continue
        if norm_subtype and norm == norm_subtype:
            continue
        if norm in customer_norms:
            continue
        if norm in _ignore:
            continue
        if norm in _STOP_WORDS:
            continue
        if norm in _BOILERPLATE_WORDS:
            continue
        if len(word) < _MIN_WORD_LEN:
            continue
        result.append(word)
    return result


# ──────────────────────────────────────────────────────────────────────────────
# Per-file validation
# ──────────────────────────────────────────────────────────────────────────────

def _check_file(
    filepath: Path,
    customer_prefix: Optional[str],
    alias_map: Optional[_AliasMap] = None,
    ignore_words: Optional[Set[str]] = None,
    config_root: Optional[Path] = None,
) -> List[tuple[str, str]]:
    """
    Validate the naming conventions for a single XML file.

    Performs three checks on the file:
      1. Directory hierarchy: Ensures the file is under a directory matching
         the object type (or an acceptable alias).
      2. Object type in filename: Ensures the filename contains the object type.
      3. Significant words: Ensures all significant words from the object name
         appear in the filename (or an accepted alias), with context-aware
         severity (WARN if implied by ancestor directories, ERROR otherwise).

    Args:
        filepath: Absolute path to the XML file to validate.
        customer_prefix: Optional customer/developer prefix to exclude from
            the significant-words check (e.g. 'UPenn', 'IDW').
        alias_map: Optional dict mapping normalized full words to sets of
            accepted abbreviations (from config file).
        ignore_words: Optional set of normalized words that are never required
            in the filename (from config file).
        config_root: Optional Path to the config directory root; used to scope
            directory checks to only subdirectories within the root.

    Returns:
        A (possibly empty) list of ``(level, message)`` tuples where *level*
        is ``'ERROR'`` or ``'WARN'`` and *message* is a human-readable error/
        warning description.
    """
    errors: List[tuple] = []

    # Parse just enough XML to read the root element.
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
    except ET.ParseError as exc:
        errors.append(('ERROR', f"XML parse error: {exc}"))
        return errors

    # Files rooted at <sailpoint> are typically multi-object import bundles and
    # are skipped.  However, a legacy export tool sometimes wrapped a single
    # object in <sailpoint>; in that case we unwrap and validate as normal.
    # Files containing an <ImportAction> element are partial-object patches
    # that can't be validated by name, so they are always skipped.
    if root.tag.lower() == 'sailpoint':
        children = list(root)
        if any(c.tag.lower() == 'importaction' for c in children):
            return errors
        if len(children) == 1:
            root = children[0]
        else:
            return errors

    # Strip any XML namespace prefix (e.g. "{ns}Rule" → "Rule").
    object_type = root.tag.split('}')[-1]
    object_name = (root.get('name') or '').strip()
    object_subtype = (root.get('type') or '').strip() or None

    if not object_name:
        # Object has no name attribute – nothing to validate.
        return errors

    filename_stem = filepath.stem          # filename without the .xml extension
    norm_filename = _normalize(filename_stem)
    norm_type = _normalize(object_type)

    # ── 1. Directory-hierarchy check ──────────────────────────────────────────
    # At least one ancestor directory must match the object type.
    # We allow the directory name to *start with* the type to accommodate
    # conventions like a "RuleLibrary/" folder for <Rule> objects.
    # Special case: Workgroup is a sub-type of Identity in SailPoint, so a
    # "Workgroup" directory is an acceptable home for <Identity> objects.
    #
    # ancestor_dirs is scoped to directories *within* the config root so that
    # parent folders of the root (e.g. the repo checkout dir) are not matched.
    ancestor_dirs = filepath.parts[:-1]   # every path component except the filename
    if config_root is not None:
        try:
            rel_parts = filepath.relative_to(config_root).parts[:-1]
            scoped_ancestor_dirs = rel_parts
        except ValueError:
            scoped_ancestor_dirs = ancestor_dirs
    else:
        scoped_ancestor_dirs = ancestor_dirs
    acceptable_types = {norm_type}
    if norm_type == _normalize('Identity'):
        acceptable_types.add(_normalize('Workgroup'))
    if norm_type == _normalize('TaskDefinition'):
        acceptable_types.add(_normalize('Report'))
        acceptable_types.add(_normalize('Reports'))
    if norm_type == _normalize('Configuration'):
        acceptable_types.add(_normalize('SystemConfiguration'))
        acceptable_types.add(_normalize('Config'))
    type_in_hierarchy = any(
        _normalize(d) in acceptable_types or _normalize(d).startswith(norm_type)
        for d in scoped_ancestor_dirs
    )
    if not type_in_hierarchy:
        errors.append((
            'ERROR',
            f"No directory in the path matches object type '{object_type}' "
            f"(expected a folder named '{object_type}' or '{object_type}…' "
            f"somewhere in the path)",
        ))

    # ── 2. Object type in filename ────────────────────────────────────────────
    # The object type (e.g. "Rule", "Workflow") must appear in the filename
    # stem (after normalization, so "rulelibrary" satisfies "rule").
    # Acceptable aliases (e.g. "workgroup" for "identity") are also accepted.
    type_in_filename = any(
        t in norm_filename or norm_filename.startswith(t)
        for t in acceptable_types
    )
    
    if not type_in_filename:
        errors.append((
            'ERROR',
            f"Object type '{object_type}' not found in filename '{filename_stem}'",
        ))
    else:
        # ── 2b. Object type position in filename ──────────────────────────────
        # When the type IS present, it should appear in the first or second
        # dash-separated segment of the filename.
        # e.g. "Rule-SomeName" (pos 1) and "Customer-Rule-SomeName" (pos 2)
        # are both acceptable, but "Customer-SomeName-Rule" (pos 3) is not.
        stem_segments = filename_stem.split('-')
        norm_early_segs = [_normalize(s) for s in stem_segments[:2]]
        type_in_early_position = any(
            any(t in seg or seg.startswith(t) for t in acceptable_types)
            for seg in norm_early_segs
        )
        if not type_in_early_position:
            errors.append((
                'WARN',
                f"Object type '{object_type}' appears in filename '{filename_stem}' "
                f"but not in the first or second dash-separated position",
            ))

    # ── 3. Significant name-words in filename ─────────────────────────────────
    # Every word that is "significant" (not the type, not the customer prefix,
    # not a stop word, not too short) must appear—normalized—in the filename.
    # Aliases (abbreviations) defined in the aliases file are also accepted.
    # If a missing word is implied by an ancestor directory name, emit a WARN
    # instead of an ERROR.
    sig_words = _significant_words(object_name, object_type, customer_prefix, object_subtype, ignore_words)
    _aliases = alias_map or {}
    norm_ancestor_dirs = [_normalize(d) for d in scoped_ancestor_dirs]

    def _word_in_filename(word: str) -> bool:
        norm = _normalize(word)
        if norm in norm_filename:
            return True
        return any(abbrev in norm_filename for abbrev in _aliases.get(norm, set()))

    def _word_implied_by_dir(word: str) -> bool:
        norm = _normalize(word)
        return any(norm in d for d in norm_ancestor_dirs)

    missing = [w for w in sig_words if not _word_in_filename(w)]
    if missing:
        warn_words = [w for w in missing if _word_implied_by_dir(w)]
        error_words = [w for w in missing if not _word_implied_by_dir(w)]
        if warn_words:
            errors.append((
                'WARN',
                f"Filename '{filename_stem}' is missing words from object name "
                f"'{object_name}' (implied by directory name): {warn_words}",
            ))
        if error_words:
            errors.append((
                'ERROR',
                f"Filename '{filename_stem}' is missing words from object name "
                f"'{object_name}': {error_words}",
            ))

    return errors


# ──────────────────────────────────────────────────────────────────────────────
# Directory scanner
# ──────────────────────────────────────────────────────────────────────────────

def _scan_directory(
    config_dir: str,
    customer_prefix: Optional[str],
    alias_map: Optional[_AliasMap] = None,
    ignore_words: Optional[Set[str]] = None,
    exclude_set: Optional[Set[str]] = None,
) -> int:
    """
    Recursively scan a config directory and validate all XML files.

    Prints one line per error/warning to stdout (in the format
    'relative_path: LEVEL - message'), and counts only ERRORs toward the
    return value (WARNs are informational only).

    Args:
        config_dir: Path to the config directory to scan.
        customer_prefix: Optional customer/developer prefix to exclude from
            significant-words checks.
        alias_map: Optional dict of word abbreviations (from config file).
        ignore_words: Optional set of words to exclude from checks
            (from config file).
        exclude_set: Optional set of file/directory paths to exclude
            (from config file).

    Returns:
        The total number of ERROR-level issues found (WARNs are not counted).
    """
    config_path = Path(config_dir)
    if not config_path.is_dir():
        print(f"ERROR: '{config_dir}' is not a directory", file=sys.stderr)
        return 1

    _excludes = exclude_set or set()

    def _is_excluded(xml_file: Path) -> bool:
        """
        Check if a file matches any entry in the exclude set.

        Paths ending with '/' are treated as directory prefixes and match any
        file whose relative path starts with that prefix (case-insensitive).
        Other entries must match the entire relative path exactly
        (case-insensitive).

        Args:
            xml_file: The file to check.

        Returns:
            True if the file matches an exclude rule, False otherwise.
        """
        try:
            rel = xml_file.relative_to(config_path)
        except ValueError:
            return False
        rel_lower = str(rel).lower()
        for entry in _excludes:
            if entry.endswith('/'):
                if rel_lower.startswith(entry):
                    return True
            else:
                if rel_lower == entry:
                    return True
        return False

    error_count = 0
    for xml_file in sorted(config_path.rglob('*.xml')):
        if _is_excluded(xml_file):
            continue
        for level, message in _check_file(xml_file, customer_prefix, alias_map, ignore_words, config_path):
            display_path = xml_file.relative_to(config_path)
            print(f"{display_path}: {level} - {message}")
            if level == 'ERROR':
                error_count += 1

    return error_count


# ──────────────────────────────────────────────────────────────────────────────
# Entry point
# ──────────────────────────────────────────────────────────────────────────────

def main() -> None:
    """
    Parse command-line arguments and execute the validation workflow.

    Loads configuration from a JSON file (if --config is specified), then
    scans one or more config directories for XML files and validates each one.
    Prints validation results to stdout and exits with an appropriate code:
      0 if all checks pass or only WARNs are found.
      1 if any ERRORs are found.
      2 if configuration or I/O errors occur.
    """
    parser = argparse.ArgumentParser(
        description=(
            "Verify that SailPoint XML config filenames match the naming "
            "conventions implied by the object type and name declared inside "
            "each file."
        )
    )
    parser.add_argument(
        '--customer',
        metavar='PREFIX',
        default=None,
        help=(
            "The customer or developer prefix used in object names and "
            "filenames (e.g. 'UPenn', 'IDW').  Words matching this prefix "
            "are treated as optional and are not required to appear in the "
            "filename."
        ),
    )
    parser.add_argument(
        '--config',
        metavar='FILE',
        default=None,
        help=(
            "Path to a JSON configuration file.  Supported keys: "
            "'aliases' (object mapping full words to lists of accepted "
            "abbreviations, e.g. {\"Affiliates\": [\"Affs\", \"Aff\"]}) and "
            "'exclude' (list of paths relative to each config directory to "
            "skip, case-insensitive; append '/' to exclude an entire "
            "subdirectory, e.g. \"Configuration/\")."
        ),
    )
    parser.add_argument(
        'config_dirs',
        nargs='+',
        metavar='CONFIG_DIR',
        help='One or more config directories to scan recursively.',
    )
    args = parser.parse_args()

    alias_map: Optional[_AliasMap] = None
    ignore_words: Optional[Set[str]] = None
    exclude_set: Optional[Set[str]] = None
    customer: Optional[str] = args.customer
    if args.config:
        try:
            alias_map, ignore_words, exclude_set, customer = _load_config(args.config, args.customer)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"ERROR: could not load config file: {exc}", file=sys.stderr)
            sys.exit(2)

    total_errors = 0
    for config_dir in args.config_dirs:
        total_errors += _scan_directory(config_dir, customer, alias_map, ignore_words, exclude_set)

    if total_errors:
        print(f"\n{total_errors} naming error(s) found.", file=sys.stderr)
        sys.exit(1)
    else:
        print("All filenames are valid.")


if __name__ == '__main__':
    main()

