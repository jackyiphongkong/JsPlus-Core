/**
 * PruneJars - build utility used by build.xml's prune-artifacts target.
 *
 * Keeps only the N newest *.jar files (by last-modified time) inside each
 * directory passed on the command line. Directories are passed as alternating
 * "dir keepCount" pairs, e.g.:
 *
 *   java PruneJars docs/apis 10 docs/reports 10
 *
 * Older archives are deleted in ascending last-modified order; anything that
 * could not be deleted is reported on stderr but does not stop the build
 * (the caller decides whether to fail via failonerror). Missing directories
 * or directories with fewer than keepCount jars are left untouched.
 */
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

public class PruneJars {

    private static final FilenameFilter JAR_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar");
        }
    };

    /**
     * Entry point: parses alternating (directory, keepCount) pairs and prunes
     * each directory.
     *
     * @param args alternating directory path and keep-count integers
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: PruneJars <dir> <keep> [<dir> <keep> ...]");
            System.exit(2);
        }
        for (int i = 0; i + 1 < args.length; i += 2) {
            try {
                prune(new File(args[i]), Integer.parseInt(args[i + 1]));
            } catch (NumberFormatException nfe) {
                System.err.println("WARN: invalid keep-count '" + args[i + 1] + "' for dir '" + args[i] + "', skipping");
            }
        }
    }

    /**
     * Deletes all but the newest {@code keep} jar files inside {@code dir}.
     *
     * @param dir  directory to prune (does not need to exist)
     * @param keep number of most recently modified jars to retain (>= 0)
     */
    private static void prune(File dir, int keep) {
        if (keep < 0) {
            System.err.println("WARN: keep must be >= 0, got " + keep + " for dir '" + dir + "', skipping");
            return;
        }
        File[] jars = dir.listFiles(JAR_FILTER);
        if (jars == null) {
            System.out.println("PruneJars: " + dir + " does not exist or is not a directory, skipping");
            return;
        }
        if (jars.length <= keep) {
            System.out.println("PruneJars: " + dir + " has " + jars.length + " jar(s), keeping all (<= " + keep + ")");
            return;
        }
        Arrays.sort(jars, new Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        int removed = 0;
        for (int i = 0; i < jars.length - keep; i++) {
            if (jars[i].delete()) {
                removed++;
            } else {
                System.err.println("WARN: could not delete " + jars[i]);
            }
        }
        System.out.println("PruneJars: " + dir + " removed " + removed
                + " jar(s), kept " + keep + " of " + jars.length);
    }
}
