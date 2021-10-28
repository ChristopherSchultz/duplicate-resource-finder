package net.christopherschultz.drf;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A utility to find duplicate resources in a series of JAR files.
 *
 * This utility looks for .class files by default, but can be configured
 * to look for any kind of path.
 *
 * @author Christopher Schultz
 */
public class DuplicateResourceFinder {
    public static void usage(PrintStream out) {
        out.println("Usage: java DuplicateResourceFinder [options] path [...]");
        out.println("");
        out.println("Options:");
        out.println(" -h, --help           Shows this help text and exits.");
        out.println(" -f, --filter <regex> A regular expression to match filenames to check for duplicates. Defaults to .*\\.*class$");
        out.println(" -p, --print          Prints all scanned paths, their sources, and file sizes.");
        out.println(" --                   End option processing and treat remaining arguments as filenames.");
    }

    public static void main(String[] args) throws Exception {
        int argindex = 0;

        FilenameFilter filter = null;
        boolean dumpPaths = false;

        while(argindex < args.length) {
            String arg = args[argindex++];

            if("--filter".equals(arg) || "-f".equals(arg)) {
                filter = new RegexFilenameFilter(args[argindex++]);
            } else if("--help".equals(arg) || "-h".equals(arg)) {
                usage(System.out);

                System.exit(0);
            } else if("--print".equals(arg) || "-p".equals(arg)) {
                dumpPaths = true;
            } else if("--".equals(arg)) {
                break; // Stop processing arguments
            } else if(arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
                System.err.println();

                usage(System.err);

                System.exit(1);
            } else {
                argindex--;
                break; // Remaining items are filenames
            }
        }

        if(null == filter) {
            filter = new FileExtensionFilter(".class");
        }

        long duplicates = 0;
        long files = 0;
        long directories = 0;

        if(argindex >= args.length) {
            System.out.println("No path(s) specified.");
            System.out.println();

            usage(System.err);

            System.exit(0);
        } else {
            HashMap<String,FileInfo> paths = new HashMap<String,FileInfo>();

            while(argindex < args.length) {
                String filename = args[argindex++];

                File file = new File(filename);

                String lowercaseFilename = filename.toLowerCase();
                if(file.isDirectory()) {
                    ++directories;

                    duplicates += scanDir(file, filter, paths);
                } else if(lowercaseFilename.endsWith(".jar") ||  lowercaseFilename.endsWith(".zip")) {
                    ++files;

                    duplicates += scan(file, filter, paths);
                } else {
                    System.err.println("Ignoring non-JAR/ZIP file " + filename);
                }
            }
            if(dumpPaths) {
                for(Map.Entry<String,FileInfo> entry : paths.entrySet()) {
                    System.out.print(entry.getKey());
                    System.out.print(": ");
                    System.out.println(entry.getValue());
                }
            }
        }

        MessageFormat fmt = new MessageFormat("Found {0} duplicate {0,choice,0#paths|1#1file|1<files} in {1,choice,0#0 files|1#1 file|1<{1} files} and {2,choice,0#0 directories|1#1 directory|1<{2} directories}.");
        System.out.println(fmt.format(new Object[] { duplicates, files, directories }));
    }

    /**
     * Scan a ZIP (or JAR) file for candidate paths, recording found paths and
     * checking for duplicates.
     *
     * @param file The ZIP (or JAR) file to scan.
     * @param filter The filename filter to apply to files in the ZIP file.
     * @param paths The record of previously-scanned paths.
     *
     * @return The number of duplicate paths found.
     *
     * @throws IOException If there is a problem reading the ZIP file.
     */
    public static long scan(File file, FilenameFilter filter, Map<String,FileInfo> paths)
        throws IOException
    {
        long duplicates = 0;

        try(ZipFile zip = new ZipFile(file)) {

            for(Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();

                String path = entry.getName();
                if(null == filter || filter.accept(null, path)) {
                    if(paths.containsKey(path)) {
                        FileInfo info = paths.get(path);
                        if(entry.getSize() == info.getFileSize()) {
                            System.out.println("File " + file + " contains path " + path + " which duplicates a path from " + info.getParentFilename() + " with same file size");
                        } else {
                            System.out.println("File " + file + " contains path " + path + " which duplicates a path from " + info.getParentFilename() + " with a different file size (" + entry.getSize() + " != " + info.getFileSize() + ")");
                        }
                        ++duplicates;
                    } else {
                        paths.put(path, new FileInfo(file.getName(), entry.getSize()));
                    }
                }
            }
        }

        return duplicates;
    }

    /**
     * Scan a directory for candidate paths, recording found paths and
     * checking for duplicates.
     *
     * @param dir The directory to scan.
     * @param filter The filename filter to apply to files in the directory.
     * @param paths The record of previously-scanned paths.
     *
     * @return The number of duplicate paths found.
     *
     * @throws IOException If there is a problem reading the ZIP file.
     */
    public static long scanDir(File dir, FilenameFilter filter, Map<String,FileInfo> paths)
        throws IOException
    {
        return scanSubdir(dir, dir, filter, paths);
    }

    private static long scanSubdir(File baseDir, File subdir, FilenameFilter filter, Map<String,FileInfo> paths)
    {
        long duplicates = 0;

        File[] children = subdir.listFiles();

        if(null != children) {
            for(File f : children) {
                if(f.isDirectory()) {
                    if(!(".".equals(f.getName()) || "..".equals(f.getName()))) {
                        duplicates += scanSubdir(baseDir, f, filter, paths);
                    }
                } else {
                    String path = baseDir.toPath().relativize(f.toPath()).toString();

                    if(null == filter || filter.accept(null, path)) {
                        if(paths.containsKey(path)) {
                            FileInfo info = paths.get(path);

                            if(f.length() == info.getFileSize()) {
                                System.out.println("Directory " + baseDir + " contains path " + path + " which duplicates a path from " + info.getParentFilename() + " with same file size");
                            } else {
                                System.out.println("Directory " + baseDir + " contains path " + path + " which duplicates a path from " + info.getParentFilename() + " with a different file size (" + f.length() + " != " + info.getFileSize() + ")");
                            }

                            ++duplicates;
                        } else {
                            paths.put(path, new FileInfo(baseDir.getName(), f.length()));
                        }
                    }
                }
            }
        }

        return duplicates;
    }

    private static class FileInfo {
        private final String parentFilename;
        private final long fileSize;

        public FileInfo(String parentFilename, long fileSize) {
            this.parentFilename = parentFilename;
            this.fileSize = fileSize;
        }

        public String getParentFilename() {
            return parentFilename;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String toString() {
            return "{ " + parentFilename + ", size=" + fileSize + " }";
        }
    }

    private static class FileExtensionFilter
        implements FilenameFilter
    {
        private String extension;

        public FileExtensionFilter(String extension) {
            this.extension = extension;
        }

        @Override
        public boolean accept(File dir, String name) {
            return null != name && name.endsWith(extension);
        }
    }

    private static class RegexFilenameFilter
        implements FilenameFilter
    {
        private Pattern pattern;

        public RegexFilenameFilter(String pattern) {
            this.pattern = Pattern.compile(pattern);
        }

        @Override
        public boolean accept(File dir, String name) {
            return null != name && pattern.matcher(name).matches();
        }
    }
}
