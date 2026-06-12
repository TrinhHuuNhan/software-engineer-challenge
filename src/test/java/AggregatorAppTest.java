import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;


public class AggregatorAppTest {

    public static void main(String[] args) throws Exception {
        testAggregateByCampaignId();
        testSkipInvalidRows();
        testParseSpendInMicros();
        testCtrAndCpaCalculation();

        System.out.println("All tests passed.");
    }

    private static void testAggregateByCampaignId() throws IOException {
        Path file = Files.createTempFile("ad-data-test", ".csv");

        Files.write(file, String.join("\n",
                "campaign_id,date,impressions,clicks,spend,conversions",
                "camp_1,2025-04-18,100,10,12.34,2",
                "camp_1,2025-04-19,200,20,3.66,1",
                "camp_2,2025-04-18,50,5,10.00,5"
        ).getBytes(StandardCharsets.UTF_8));

        Map<String, AggregatorApp.CampaignStatus> result = AggregatorApp.aggregate(file);

        AggregatorApp.CampaignStatus camp1 = result.get("camp_1");

        assertEquals(300L, camp1.getTotalImpressions(), "camp_1 impressions");
        assertEquals(30L, camp1.getTotalClicks(), "camp_1 clicks");
        assertEquals(16_000_000L, camp1.getTotalSpendInMicros(), "camp_1 spend");
        assertEquals(3L, camp1.getTotalConversions(), "camp_1 conversions");

        Files.deleteIfExists(file);
    }

    private static void testSkipInvalidRows() throws IOException {
        Path file = Files.createTempFile("ad-data-invalid-test", ".csv");

        Files.write(file, String.join("\n",
                "campaign_id,date,impressions,clicks,spend,conversions",
                "camp_1,2025-04-18,100,10,12.34,2",
                "camp_invalid,invalid-date,100,10,12.34,2",
                "camp_2,2025-04-18,50,5,10.00,1"
        ).getBytes(StandardCharsets.UTF_8));

        Map<String, AggregatorApp.CampaignStatus> result = AggregatorApp.aggregate(file);

        assertEquals(2, result.size(), "valid campaign count");
        assertTrue(result.containsKey("camp_1"), "camp_1 exists");
        assertTrue(result.containsKey("camp_2"), "camp_2 exists");
        assertTrue(!result.containsKey("camp_invalid"), "invalid row skipped");

        Files.deleteIfExists(file);
    }

    private static void testParseSpendInMicros() {
        assertEquals(12_000_000L, AggregatorApp.parseSpendInMicros("12"), "spend 12");
        assertEquals(12_300_000L, AggregatorApp.parseSpendInMicros("12.3"), "spend 12.3");
        assertEquals(12_340_000L, AggregatorApp.parseSpendInMicros("12.34"), "spend 12.34");
        assertEquals(12_345_678L, AggregatorApp.parseSpendInMicros("12.345678"), "spend 12.345678");
    }

    private static void testCtrAndCpaCalculation() {
        AggregatorApp.CampaignStatus stats = new AggregatorApp.CampaignStatus(
                "camp_1",
                100L,
                25L,
                10_000_000L,
                2L
        );

        assertDoubleEquals(0.25, stats.getCtr(), "CTR");
        assertDoubleEquals(5.0, stats.getCpa(), "CPA");
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertDoubleEquals(double expected, double actual, String message) {
        double epsilon = 0.000001;

        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
        }
    }


}
