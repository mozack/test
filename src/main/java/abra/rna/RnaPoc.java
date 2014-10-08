package abra.rna;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import abra.NativeAssembler;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord;

public class RnaPoc {
	
	public static int MAX_INTRON_LENGTH = 1000000;
	
	private BufferedWriter contigWriter;

	public void run(String input, String output) throws IOException {
		
		contigWriter = new BufferedWriter(new FileWriter(output, false));
		
		List<SAMRecord> currReads = new ArrayList<SAMRecord>();
		
		SAMFileReader reader = new SAMFileReader(new File(input));
		reader.setValidationStringency(ValidationStringency.SILENT);
		
		SAMRecord lastRead = null;

		for (SAMRecord read : reader) {
			if (read.getMappingQuality() > 0) {
				if (lastRead == null || (read.getAlignmentStart()-lastRead.getAlignmentStart()) < MAX_INTRON_LENGTH) {
					currReads.add(read);
				} else {
					processReads(currReads);
					currReads.clear();
					currReads.add(read);
				}
				
				lastRead = read;
			}
		}
		
		reader.close();
		contigWriter.close();
	}
	
	private void processReads(List<SAMRecord> reads) throws IOException {

		NativeAssembler assem = newAssembler();
		
		String contigs = assem.simpleAssemble(reads);
		
		if (!contigs.equals("<ERROR>") && !contigs.equals("<REPEAT>") && !contigs.isEmpty()) {
			contigWriter.write(contigs);
		}
	}
	
	private NativeAssembler newAssembler() {
		NativeAssembler assem = new NativeAssembler();

		assem.setTruncateOutputOnRepeat(true);
		assem.setMaxContigs(1000);

		assem.setMaxPathsFromRoot(100000);
		assem.setReadLength(75);
		assem.setKmer(new int[] { 17, 27, 37, 47 });
		assem.setMinKmerFrequency(2);
		assem.setMinBaseQuality(40);
		
		// The following params not used
		assem.setMinReadCandidateFraction(0);
		assem.setMaxAverageDepth(0);
		assem.setShouldSearchForSv(false);
		assem.setAverageDepthCeiling(0);

		return assem;		
	}
	
	public static void main(String[] args) throws IOException {
		RnaPoc poc = new RnaPoc();
		
		poc.run(args[0], args[1]);
	}
}