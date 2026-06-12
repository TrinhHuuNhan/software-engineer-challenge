import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;


public class AggregatorApp {

    private static final long SPEND_SCALE = 1_000_000L;
    private static final int TOTAL_COLUMNS = 6;
    private static final int TOP_N = 10;

    private static final LocalDate TODAY = LocalDate.now();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DecimalFormat CTR_FORMAT;
    private static final DecimalFormat MONEY_FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);

        CTR_FORMAT = new DecimalFormat("0.0000", symbols);
        CTR_FORMAT.setRoundingMode(RoundingMode.HALF_UP);

        MONEY_FORMAT = new DecimalFormat("0.00", symbols);
        MONEY_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    public static void logMemory(String step) {
        Runtime runtime = Runtime.getRuntime();

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();

        System.out.printf(
                "[%s] Used: %.2f MB | Total allocated: %.2f MB | Max heap: %.2f MB%n",
                step,
                usedMemory / 1024.0 / 1024.0,
                totalMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0
        );
    }


    public static void main(String[] args) throws IOException {
        logMemory("start");

        CliArgs cliArgs = CliArgs.parse(args);
        Path inputPath = cliArgs.getInputPath();
        Path outputDir = cliArgs.getOutputDir();

        long aggregateStart = System.currentTimeMillis();
        Map<String, CampaignStatus> campaignStatuses = aggregate(inputPath);
        long aggregateEnd = System.currentTimeMillis();
        logMemory("aggregate");

        Files.createDirectories(outputDir);

        writeTop10HighestCtr(campaignStatuses, outputDir.resolve("top10_ctr.csv"));
        writeTop10LowestCpa(campaignStatuses, outputDir.resolve("top10_cpa.csv"));

        long endTime = System.currentTimeMillis();

        System.out.printf("Aggregation time: %.2f seconds%n", (aggregateEnd - aggregateStart) / 1000.0);
        System.out.printf("Total processing time: %.2f seconds%n", (endTime - aggregateStart) / 1000.0);
    }

    private static void writeTop10LowestCpa(Map<String, CampaignStatus> campaignStatusMap, Path outputPath) throws IOException {
        List<CampaignStatus> topCpa = campaignStatusMap.values()
                .stream()
                .filter(stats -> stats.getTotalConversions() > 0)
                .sorted(Comparator.comparingDouble(CampaignStatus::getCpa))
                .limit(TOP_N)
                .collect(Collectors.toList());
        write(outputPath, topCpa);
    }

    private static void writeTop10HighestCtr(Map<String, CampaignStatus> campaignStatusMap, Path outputPath) throws IOException {
        List<CampaignStatus> topCtr = campaignStatusMap.values()
                .stream()
                .filter(stats -> stats.getTotalImpressions() > 0)
                .sorted(Comparator.comparingDouble(CampaignStatus::getCtr).reversed())
                .limit(TOP_N)
                .collect(Collectors.toList());
        write(outputPath, topCtr);
    }

    private static void write(Path outputPath, List<CampaignStatus> data) throws IOException {

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA");
            writer.newLine();

            for (CampaignStatus stats : data) {
                writer.write(toCsvLine(stats));
                writer.newLine();
            }
        }
    }

    private static String toCsvLine(CampaignStatus stats) {
        return String.join(",",
                stats.getCampaignId(),
                String.valueOf(stats.getTotalImpressions()),
                String.valueOf(stats.getTotalClicks()),
                formatSpend(stats.getTotalSpendInMicros()),
                String.valueOf(stats.getTotalConversions()),
                formatCtr(stats),
                formatCpa(stats)
        );
    }

    private static String formatCtr(CampaignStatus stats) {
        return CTR_FORMAT.format(stats.getCtr());
    }

    private static String formatCpa(CampaignStatus stats) {
        if (stats.getTotalConversions() == 0L) {
            return "";
        }

        return MONEY_FORMAT.format(stats.getCpa());
    }

    private static String formatSpend(long spendMicros) {
        double spend = spendMicros / (double) SPEND_SCALE;
        return MONEY_FORMAT.format(spend);
    }


    static Map<String, CampaignStatus> aggregate(Path inputPath) throws IOException {
        if (inputPath == null) {
            throw new IllegalArgumentException("Input path is null");
        }

        Map<String, CampaignStatus> campaignStatuses = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {

            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("Input path does not contain header");
            }

            String line;
            while ((line = reader.readLine()) != null) {

                try {

                    if (line.isEmpty()) {
                        throw new IllegalArgumentException("Input line is null");
                    }

                    String[] columns = line.split(",", -1);
                    if (columns.length != TOTAL_COLUMNS) {
                        throw new IllegalArgumentException("Input line contains invalid columns");
                    }

                    String campaignId = parseCampaignId(columns[0]);
                    validateDate(columns[1]);
                    long impressions = parseLong(columns[2]);
                    long clicks = parseLong(columns[3]);
                    long spendInMicros = parseSpendInMicros(columns[4]);
                    long conversions = parseLong(columns[5]);


                    CampaignStatus stats = campaignStatuses.get(campaignId);
                    if (stats == null) {
                        stats = new CampaignStatus(campaignId, impressions, clicks, spendInMicros, conversions);
                        campaignStatuses.put(campaignId, stats);
                    } else {
                        stats.addImpressions(impressions);
                        stats.addClicks(clicks);
                        stats.addConversions(conversions);
                        stats.addSpendInMicros(spendInMicros);
                    }

                } catch (Exception ignored) {
                }

            }
        }
        return campaignStatuses;
    }

    static long parseSpendInMicros(String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Spend is empty");
        } else {

            if (text.startsWith("-")) {
                throw new IllegalArgumentException("Spend is negative");
            }

            String[] parts = text.split("\\.", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("Spend contains more than 2 parts");
            }

            // .12 -> whole = 0
            long whole = parts[0].isEmpty() ? 0L : Long.parseLong(parts[0]);
            StringBuilder fraction = new StringBuilder(parts.length == 2 ? parts[1] : "");
            if (fraction.length() > 6) {
                fraction = new StringBuilder(fraction.substring(0, 6));
            }

            // 1.3 -> 1.300000
            while (fraction.length() < 6) {
                fraction.append("0");
            }
            long micros = Long.parseLong(fraction.toString());
            return Math.addExact(
                    Math.multiplyExact(whole, SPEND_SCALE),
                    micros
            );
        }
    }

    private static long parseLong(String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Input value is null");
        } else {
            long parsed = Long.parseLong(text);
            if (parsed < 0L) {
                throw new IllegalArgumentException("Input value is negative");
            } else {
                return parsed;
            }
        }

    }

    private static void validateDate(String column) {
        if (column == null) {
            throw new IllegalArgumentException("Input column is null");
        }

        LocalDate date;

        try {
            date = LocalDate.parse(column.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format, expected yyyy-MM-dd: " + column);
        }

        if (date.isAfter(TODAY)) {
            throw new IllegalArgumentException("Date is after today");
        }
    }

    private static String parseCampaignId(String campaignId) {
        if (campaignId == null) {
            throw new IllegalArgumentException("Input column campaignId is null");
        }
        String text = campaignId.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Input column campaignId is empty");
        }

        return text;

    }

    static class CampaignStatus {
        String campaignId;
        long totalImpressions;
        long totalClicks;
        long totalSpendInMicros;
        long totalConversions;

        public CampaignStatus(String campaignId, long totalImpressions, long totalClicks, long totalSpendInMicros, long totalConversions) {
            this.campaignId = campaignId;
            this.totalImpressions = totalImpressions;
            this.totalClicks = totalClicks;
            this.totalSpendInMicros = totalSpendInMicros;
            this.totalConversions = totalConversions;
        }


        void addClicks(long totalClicks) {
            this.totalClicks = Math.addExact(this.totalClicks, totalClicks);
        }

        void addImpressions(long totalImpressions) {
            this.totalImpressions = Math.addExact(this.totalImpressions, totalImpressions);
        }

        void addSpendInMicros(long totalSpendInMicros) {
            this.totalSpendInMicros = Math.addExact(this.totalSpendInMicros, totalSpendInMicros);
        }

        void addConversions(long totalConversions) {
            this.totalConversions = Math.addExact(this.totalConversions, totalConversions);
        }

        public long getTotalImpressions() {
            return totalImpressions;
        }

        public double getCtr() {
            return totalImpressions == 0L ? 0L : (totalClicks / (double) totalImpressions);
        }

        public double getCpa() {
            if (totalConversions == 0L) {
                return 0L;
            }
            double spend = totalSpendInMicros / (double) SPEND_SCALE;
            return spend / totalConversions;
        }

        public String getCampaignId() {
            return campaignId;
        }

        public long getTotalClicks() {
            return totalClicks;
        }

        public long getTotalSpendInMicros() {
            return totalSpendInMicros;
        }

        public long getTotalConversions() {
            return totalConversions;
        }
    }

    static class CliArgs {
        private final Path inputPath;
        private final Path outputDir;

        private CliArgs(Path inputPath, Path outputDir) {
            this.inputPath = inputPath;
            this.outputDir = outputDir;
        }

        public Path getInputPath() {
            return inputPath;
        }

        public Path getOutputDir() {
            return outputDir;
        }

        static CliArgs parse(String[] args) {
            Path input = null;
            Path output = null;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                if ("--input".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --input");
                    }
                    input = Paths.get(args[++i]);
                } else if ("--output".equals(arg)) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --output");
                    }
                    output = Paths.get(args[++i]);
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (input == null) {
                throw new IllegalArgumentException("--input is required");
            }

            if (output == null) {
                throw new IllegalArgumentException("--output is required");
            }

            if (!Files.exists(input)) {
                throw new IllegalArgumentException("Input file does not exist: " + input);
            }

            if (!Files.isRegularFile(input)) {
                throw new IllegalArgumentException("Input path is not a file: " + input);
            }

            return new CliArgs(input, output);
        }
    }


}
