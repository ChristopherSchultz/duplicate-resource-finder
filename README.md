# duplicate-resource-finder
A Java utility to find duplicate classes and other resources in sets of JAR files

# Building

mvn package

# Running

java -jar drf-1.0-SNAPSHOT.jar

# Usage

```
Usage: java DuplicateResourceFinder [options] path [...]

Options:
 -h, --help           Shows this help text and exits.
 -f, --filter <regex> A regular expression to match filenames to check for duplicates. Defaults to .*\.*class$
 -p, --print          Prints all scanned paths, their sources, and file sizes.
 --                   End option processing and treat remaining arguments as filenames.
```
