package org.broadinstitute.hellbender.tools.spark.pipelines.metrics;

import htsjdk.samtools.SAMTag;
import htsjdk.samtools.SAMUtils;
import org.broadinstitute.hellbender.CommandLineProgramTest;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.tools.spark.pipelines.metrics.QualityScoreDistributionSpark.Counts;
import org.broadinstitute.hellbender.utils.read.ArtificialReadUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.testutils.ArgumentsBuilder;
import org.broadinstitute.hellbender.testutils.EnvironmentTestUtils;
import org.broadinstitute.hellbender.GATKBaseTest;
import org.broadinstitute.hellbender.testutils.IntegrationTestSpec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QualityScoreDistributionSparkIntegrationTest  extends CommandLineProgramTest {

    //NOTE: these tests use the same data and results as the non-spark ones, by design
    //Note: the 'expected' results in this test come from running picard 1.130

    //Note: we don't test the contents of the chart pdf

    private static final File TEST_DATA_DIR = new File(
            "src/test/resources/org/broadinstitute/hellbender/metrics/analysis//QualityScoreDistribution");

    @Override
    public String getTestedClassName() {
        return QualityScoreDistributionSpark.class.getSimpleName();
    }

    @Test(groups = {"R", "spark"})
    public void testAccumulators() throws Exception {
        final long[] qs= new long[128];
        final long[] oqs= new long[128];

        final Counts counts = new Counts(false);
        final long[] initQs = counts.getQualCounts();
        final long[] initOQs = counts.getOrigQualCounts();
        Assert.assertEquals(initQs, qs);
        Assert.assertEquals(initOQs, oqs);

        final GATKRead read1 = ArtificialReadUtils.createArtificialRead("aa".getBytes(), new byte[]{50, 50}, "2M");
        read1.setAttribute(SAMTag.OQ.name(), SAMUtils.phredToFastq(new byte[]{30, 40}));
        counts.addRead(read1);

        qs[50]+=2;//there are two bases in the read with a quality score of 50
        oqs[30]+=1;
        oqs[40]+=1;
        final long[] oneQs = counts.getQualCounts();
        final long[] oneOQs = counts.getOrigQualCounts();
        Assert.assertEquals(oneQs, qs);
        Assert.assertEquals(oneOQs, oqs);

        final Counts counts2 = new Counts(false);

        final GATKRead read2 = ArtificialReadUtils.createArtificialRead("aa".getBytes(), new byte[]{51, 51}, "2M");
        read2.setAttribute(SAMTag.OQ.name(), SAMUtils.phredToFastq(new byte[]{31, 41}));
        counts2.addRead(read2);

        counts.merge(counts2);
        qs[51]+=2;//there are two bases in the read with a quality score of 51
        oqs[31]+=1;  //new read OQ
        oqs[41]+=1;  //new read OQ
        final long[] mergedQs = counts.getQualCounts();
        final long[] mergedOQs = counts.getOrigQualCounts();
        Assert.assertEquals(mergedQs, qs);
        Assert.assertEquals(mergedOQs, oqs);
    }

    @DataProvider(name = "QualityScoreDistribution")
    private Iterator<Object[]> makeQualityScoreDistributionData(){
        final List<Object[]> list= new ArrayList<>();
        list.add(new Object[]{"first5000a.bam", "qualscoredist.txt", null, true, false, false});
        list.add(new Object[]{"first5000a.cram", "qualscoredist.txt", b37_reference_20_21, true, false, false});
        list.add(new Object[]{"originalQuals.chr1.1-1K.bam", "originalQuals.chr1.1-1K.QualityScoreDistribution.txt", null, true, false, false});

        list.add(new Object[]{"example_pfFail_reads.bam", "pfFailBam.pf.txt", null, true, false, false});
        list.add(new Object[]{"example_pfFail_reads.bam", "pfFailBam.pfOnly.txt", null, true, true, false});

        list.add(new Object[]{"unmapped.bam", "unmappedBam.ALIGNED_READS_ONLY_false.txt", null, true, false, false});
        list.add(new Object[]{"unmapped.bam", "unmappedBam.ALIGNED_READS_ONLY_true.txt", null, false, false, true});

        return list.iterator();
    }

    @Test(dataProvider = "QualityScoreDistribution", groups = {"R", "spark"})
    public void test(final String unsortedBamName, final String expectedFileName, final String referenceName,
                     final boolean makePdf, final boolean pfReadsOnly, final boolean alignedReadsOnly) throws IOException {
        final File unsortedBam = new File(TEST_DATA_DIR, unsortedBamName);
        final File expectedFile = new File(TEST_DATA_DIR, expectedFileName);

        //Note we compare to non-spark outputs
        final File outfile = GATKBaseTest.createTempFile("test", ".metrics");
        final File pdf = GATKBaseTest.createTempFile("test", ".pdf");

        ArgumentsBuilder args = new ArgumentsBuilder();
        args.addRaw("--" + StandardArgumentDefinitions.INPUT_LONG_NAME);
        args.addRaw(unsortedBam.getCanonicalPath());
        args.addRaw("--" + StandardArgumentDefinitions.OUTPUT_LONG_NAME);
        args.addRaw(outfile.getCanonicalPath());
        if (null != referenceName) {
            final File REF = new File(referenceName);
            args.addRaw("-R");
            args.addRaw(REF.getAbsolutePath());
        }
        if (makePdf) {
            args.addRaw("--" + "chart");
            args.addRaw(pdf.getCanonicalPath());
        }
        args.addRaw("--" + "pfReadsOnly");
        args.addRaw(pfReadsOnly);
        args.addRaw("--" + "alignedReadsOnly");
        args.addRaw(alignedReadsOnly);

        this.runCommandLine(args.getArgsArray());

        IntegrationTestSpec.assertEqualTextFiles(outfile, expectedFile, "#");
    }

    @Test(
        groups = "spark",
        expectedExceptions = UserException.NotAvailableInGatkLiteDocker.class,
        singleThreaded = true
    )
    public void testInGatkLiteDocker() throws IOException {
        EnvironmentTestUtils.checkWithGATKDockerPropertySet(() -> {
            final File unsortedBam = new File(TEST_DATA_DIR, "first5000a.bam");

            //Note we compare to non-spark outputs
            final File outfile = GATKBaseTest.createTempFile("test", ".metrics");
            final File pdf = GATKBaseTest.createTempFile("test", ".pdf");

            ArgumentsBuilder args = new ArgumentsBuilder()
                .addInput(unsortedBam)
                .addOutput(outfile)
                .add("chart", pdf)
                .add("pfReadsOnly", false)
                .add("alignedReadsOnly", false);

            this.runCommandLine(args.getArgsArray());
        });
    }

    @Test
    public void testGetRScriptResource() {
        // Make sure the RScript resource can be resolved
        Assert.assertNotNull(QualityScoreDistributionSpark.getQualityScoreDistributionRScriptResource());
    }

}
