package be.tcuvelier.qdoctools.consistency;

public class ConsistencyResults {
    public String majorError;
    public int nMissingModulesInDocBook;
    public int nMissingPagesInDocBook;
    public int nMissingModulesInHTML;
    public int nMissingPagesInHTML;
    public int nDifferentModules;
    public int nDifferentFiles;

    private ConsistencyResults(String majorError, int nMissingModulesInDocBook,
            int nMissingPagesInDocBook, int nMissingModulesInHTML, int nMissingPagesInHTML,
            int nDifferentModules, int nDifferentFiles) {
        this.majorError = majorError;
        this.nMissingModulesInDocBook = nMissingModulesInDocBook;
        this.nMissingPagesInDocBook = nMissingPagesInDocBook;
        this.nMissingModulesInHTML = nMissingModulesInHTML;
        this.nMissingPagesInHTML = nMissingPagesInHTML;
        this.nDifferentModules = nDifferentModules;
        this.nDifferentFiles = nDifferentFiles;
    }

    private ConsistencyResults() {
        this("", 0, 0, 0, 0, 0, 0);
    }

    public static ConsistencyResults fromNoError() {
        return new ConsistencyResults();
    }
    public static ConsistencyResults fromMajorError(String error) {
        ConsistencyResults cr = new ConsistencyResults();
        cr.majorError = error;
        return cr;
    }
    public static ConsistencyResults fromMissingDocBookModules(int missingModules) {
        ConsistencyResults cr = new ConsistencyResults();
        cr.nMissingModulesInDocBook = missingModules;
        return cr;
    }
    public static ConsistencyResults fromMissingHTMLModules(int missingModules) {
        ConsistencyResults cr = new ConsistencyResults();
        cr.nMissingModulesInHTML = missingModules;
        return cr;
    }
    public static ConsistencyResults fromMissingDocBookPages(int missingPages) {
        ConsistencyResults cr = new ConsistencyResults();
        cr.nMissingPagesInDocBook = missingPages;
        return cr;
    }
    public static ConsistencyResults fromMissingHTMLPages(int missingPages) {
        ConsistencyResults cr = new ConsistencyResults();
        cr.nMissingPagesInHTML = missingPages;
        return cr;
    }

    public void add(ConsistencyResults cr) {
        assert majorError.isEmpty();

        majorError = cr.majorError;

        nMissingModulesInDocBook += cr.nMissingModulesInDocBook;
        nMissingPagesInDocBook += cr.nMissingPagesInDocBook;
        nMissingModulesInHTML += cr.nMissingModulesInHTML;
        nMissingPagesInHTML += cr.nMissingPagesInHTML;
        nDifferentModules += cr.nDifferentModules;
        nDifferentFiles += cr.nDifferentFiles;
    }

    public void add(ConsistencyCheckResults cr) {
        assert majorError.isEmpty();

        majorError = cr.majorError;

        if (! cr.docbook.equals(cr.html)) {
            nDifferentFiles += 1;
        }
    }

    public boolean hasErrors() {
        return ! hasNoErrors();
    }

    public boolean hasNoErrors() {
        return majorError.isEmpty() &&
                nMissingModulesInDocBook == 0 &&
                nMissingPagesInDocBook == 0 &&
                nMissingModulesInHTML == 0 &&
                nMissingPagesInHTML == 0 &&
                nDifferentModules == 0 &&
                nDifferentFiles == 0;
    }

    public String describe(String prefix) {
        final StringBuilder sb = new StringBuilder();
        if (! majorError.isEmpty()) {
            sb.append(prefix).append(majorError).append("\n");
        } else {
            if (nMissingModulesInDocBook > 0) {
                sb.append(prefix).append("nMissingModulesInDocBook: ").append(nMissingModulesInDocBook).append("\n");
            }
            if (nMissingPagesInDocBook > 0) {
                sb.append(prefix).append("nMissingFilesInDocBook: ").append(nMissingPagesInDocBook).append("\n");
            }
            if (nMissingModulesInHTML > 0) {
                sb.append(prefix).append("nMissingModulesInHTML: ").append(nMissingModulesInHTML).append("\n");
            }
            if (nMissingPagesInHTML > 0) {
                sb.append(prefix).append("nMissingFilesInHTML: ").append(nMissingPagesInHTML).append("\n");
            }
            if (nDifferentModules > 0) {
                sb.append(prefix).append("nDifferentModules: ").append(nDifferentModules).append("\n");
            }
            if (nDifferentFiles > 0) {
                sb.append(prefix).append("nDifferentFiles: ").append(nDifferentFiles).append("\n");
            }
        }
        return sb.toString();
    }
}
