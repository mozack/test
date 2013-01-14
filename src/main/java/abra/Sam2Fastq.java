package abra;

import java.io.File;
import java.io.IOException;
import java.util.List;


import net.sf.samtools.CigarElement;
import net.sf.samtools.CigarOperator;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMFileReader.ValidationStringency;
import net.sf.samtools.SAMRecord.SAMTagAndValue;

/**
 * Converts SAM/BAM file to FASTQ
 * 
 * @author Lisle Mose (lmose at unc dot edu)
 */
public class Sam2Fastq {
	
	private static final int MAX_SAM_READ_NAME_LENGTH = 255;
	
	private FastqOutputFile output1;
	private FastqOutputFile output2;
	private ReverseComplementor reverseComplementor = new ReverseComplementor();
	private boolean shouldIdentifyEndByReadId = false;
	private boolean isMapspliceFusions = false;
	private String end1Suffix;
	private String end2Suffix;

	/**
	 * Convert the input paired end SAM/BAM file into 2 fastq files.
	 * Input SAM files that contain multiple mappings should be sorted by read name.
	 */
	public void convertPairedEnd(String inputSam, String outputFastq) throws IOException {
		String last1Read = "";
		String last2Read = "";
		
        SAMFileReader reader = new SAMFileReader(new File(inputSam));
        reader.setValidationStringency(ValidationStringency.SILENT);

        output1 = new FastqOutputFile();
        output1.init(outputFastq);
        
        int output1Count = 0;
        int output2Count = 0;
        int lineCnt = 0;
        
        for (SAMRecord read : reader) {
        	if (isFirstInPair(read)) {
        		if (!read.getReadName().equals(last1Read)) {
        			output1.write(samReadToFastqRecord(read));
        			last1Read = read.getReadName();
        			output1Count += 1;
        		}
        	} else if (isSecondInPair(read)) {
        		if (!read.getReadName().equals(last2Read)) {
        			last2Read = read.getReadName();
        			read.setReadName(read.getReadName() + "_2");
        			output1.write(samReadToFastqRecord(read));
        			output2Count += 1;
        		}
        	} else {
        		System.out.println("Don't know what to do with unpaired read: " + read.getSAMString());
        	}
        	
            lineCnt++;
            if ((lineCnt % 1000000) == 0) {
                System.out.println("record: " + lineCnt);
            }
        }
                
        output1.close();
        reader.close();
        
        if (output1Count != output2Count) {
        	throw new IllegalStateException("Non-symmetrical read counts found for " + inputSam + ".  Your reads may not be paired properly.");
        }
	}
	
	/**
	 * Convert the input SAM/BAM file into a single fastq file.
	 * Input SAM files that contain multiple mappings should be sorted by read name.
	 */
	public void convert(String inputSam, String outputFastq) throws IOException {
		String last1Read = "";
		
        SAMFileReader reader = new SAMFileReader(new File(inputSam));
        reader.setValidationStringency(ValidationStringency.SILENT);

        output1 = new FastqOutputFile();
        output1.init(outputFastq);
        int lineCnt = 0;
        
        for (SAMRecord read : reader) {
    		if (!read.getReadName().equals(last1Read)) {
    			output1.write(samReadToFastqRecord(read));
    			last1Read = read.getReadName();
    		}
    		
            lineCnt++;
            if ((lineCnt % 1000000) == 0) {
                System.out.println("record: " + lineCnt);
            }
        }
                
        output1.close();
        reader.close();
	}
	
	private FastqRecord samReadToFastqRecord(SAMRecord read) {
		String bases = read.getReadString();
		String qualities = read.getBaseQualityString();
		
		if (read.getReadNegativeStrandFlag()) {
			bases = reverseComplementor.reverseComplement(bases);
			qualities = reverseComplementor.reverse(qualities);
		}
		
		read.setReadString("");
		read.setBaseQualityString("");
		// XA tag can be lengthy, so remove it.
		read.setAttribute("XA", null);
		read.setAttribute("OQ", null);
		
		String readStr = read.getSAMString();
		
		if (readStr.length() > MAX_SAM_READ_NAME_LENGTH) {
			String msg = "Error!  Max SAM Read name length exceeded for: " + readStr;
			System.out.println(msg);
			throw new RuntimeException(msg);
		}
		readStr = readStr.replace('\t','|');
		
		String readName = readStr; 
		
		FastqRecord fastq = new FastqRecord("@" + readName, bases, qualities);
		
		return fastq;
	}
	
	private boolean isFusion(SAMRecord read) {
		return read.getAttribute("ZF") != null;
	}
	
	private boolean isFirstInPair(SAMRecord read) {
		boolean isFirstInPair;
		
		if (shouldIdentifyEndByReadId) {
			isFirstInPair = read.getReadName().endsWith(end1Suffix);
			
		} else {
			isFirstInPair = read.getFirstOfPairFlag();
		}
		
		return isFirstInPair;
	}
	
	private boolean isSecondInPair(SAMRecord read) {
		boolean isSecondInPair;
		
		if (shouldIdentifyEndByReadId) {
			isSecondInPair = read.getReadName().endsWith(end2Suffix);
			
		} else {
			isSecondInPair = read.getSecondOfPairFlag();
		}
		
		return isSecondInPair;
	}
	
	public void setEndSuffixes(String end1Suffix, String end2Suffix) {
		this.shouldIdentifyEndByReadId = true;
		this.end1Suffix = end1Suffix;
		this.end2Suffix = end2Suffix;
	}
	
	public void setMapspliceFusions(boolean isMapspliceFusions) {
		this.isMapspliceFusions = isMapspliceFusions;
	}

/*
	public static void run(String[] args) throws IOException {
		Sam2FastqOptions options = new Sam2FastqOptions();
		options.parseOptions(args);
		
		if (options.isValid()) {
			long s = System.currentTimeMillis();
			System.out.println("sam2fastq starting");
			
			Sam2Fastq sam2Fastq = new Sam2Fastq();
			if (options.isPairedEnd()) {
				
				if (options.shouldIdEndByReadName()) {
					sam2Fastq.setEndSuffixes(options.getEnd1Suffix(), options.getEnd2Suffix());
				}
				
				sam2Fastq.setMapspliceFusions(options.isMapspliceFusions());
				
				sam2Fastq.convert(options.getInputFile(), options.getFastq1(), options.getFastq2());
			} else {
				sam2Fastq.convert(options.getInputFile(), options.getFastq1());
			}
			
			long e = System.currentTimeMillis();
			System.out.println("sam2fastq done.  Elapsed secs: " + (e-s)/1000);
		}
	}
*/
/*	
	public static void main(String[] args) throws Exception {
		String argz = "--end1 /1 --end2 /2 --in /home/lisle/sam2fastq/round2/test2.sam  --fastq1 /home/lisle/sam2fastq/round2/1.fastq --fastq2 /home/lisle/sam2fastq/round2/2.fastq --mapsplice";
		
		run(argz.split(" "));
	}
*/
}
