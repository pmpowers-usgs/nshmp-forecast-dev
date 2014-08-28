package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static org.opensha.eq.model.SourceType.*;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.StandardSystemProperty;

/*
 * Starting point for conversions from NHSMP *.in files to XML. To keep some order and preserve logs
 * by file type and region, the methods in this class process files in groups by type and region.
 * 
 * @author Peter Powers
 */
class Converter {

	private static final String S = StandardSystemProperty.FILE_SEPARATOR.value();
	private static final SourceManager MGR_2008 = SourceManager_2008.instance();
	private static final SourceManager MGR_2014 = SourceManager_2014.instance();
	private static final String FCAST_DIR = "forecasts" + S;
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("[yy-MM-dd-HH-mm]");
	private static final String LOG_DIR = FCAST_DIR + "logs" + S;
	private static final Level LEVEL = Level.INFO;
		
	public static void main(String[] args) {
		convert2008();
//		convert2014();
	}
	
	static void convert2008() {
		List<SourceFile> files;
		String logID = Converter.class.getName() + "-2008-" + sdf.format(new Date());
		String logPath = LOG_DIR + logID + ".log";
		Logger log = Utils.logger(logID, logPath, LEVEL);
		
		// there are some mildly confusing things that happen here, both in the
		// SourceManager class and the export process...
		//		-- anything with 'deep' in the name is converted to a 'SLAB' type
		//		-- sources in CA and CASC are moved into their respective correct
		//		   folder in WUS
		//		-- the mb CEUS grids are moved into their own folders with
		//		   custom mag-converting GMMs
		
//		files = MGR_2008.get(WUS, FAULT);
//		convertFault(files, "2008", log);
//		files = MGR_2008.get(WUS, GRID);
//		convertGrid(files, "2008", log);
//		files = MGR_2008.get(WUS, INTERFACE);
//		convertInterface(files, "2008", log);
//		files = MGR_2008.get(WUS, SLAB);
//		convertGrid(files, "2008", log);
//
//		files = MGR_2008.get(CA, FAULT);
//		convertFault(files, "2008", log);
//		files = MGR_2008.get(CA, GRID);
//		convertGrid(files, "2008", log);
//		files = MGR_2008.get(CA, SLAB);
//		convertGrid(files, "2008", log);

		files = MGR_2008.get(CEUS, FAULT);
		convertFault(files, "2008", log);
		files = MGR_2008.get(CEUS, GRID);
		convertGrid(files, "2008", log);
		files = MGR_2008.get(CEUS, CLUSTER);
		convertCluster(files, "2008", log);
	}
	
	static void convert2014() {
		List<SourceFile> files;
		
//		files = MGR_2014.get(CEUS, CLUSTER);
//		convertCluster(files, CEUS, "2014");

		
		
//		files = MGR_2014.get(WUS, FAULT);
//		convertFault(files, WUS, "2014");

//		files = MGR_2014.get(WUS, CLUSTER);
//		convertCluster(files, WUS, "2014");
		
//		files = MGR_2014.get(WUS, INTERFACE);
//		convertInterface(files, WUS, "2014");
		
		// TODO problems here -- need to be broken into different depths by latitude
//		files = MGR_2014.get(WUS, SLAB);
//		convertGrid(files, WUS, "2014");
	
		
	}

	static void convertGrid(List<SourceFile> files, String yr, Logger log) {
		String out = FCAST_DIR + yr + S;
		GridConverter converter = GridConverter.create(log);
		for (SourceFile file : files) {
			checkArgument(file.type == GRID || file.type == SLAB, "Wrong file type: %s",
				file.type.name());
			converter.convert(file, out);
		}
	}

	static void convertFault(List<SourceFile> files, String yr, Logger log) {
		String out = FCAST_DIR + yr + S;
		FaultConverter converter = FaultConverter.create(log);
		for (SourceFile file : files) {
			checkArgument(file.type == FAULT, "Wrong file type: %s", file.type.name());
			converter.convert(file, out);
		}
	}
	
	static void convertInterface(List<SourceFile> files, String yr, Logger log) {
		String out = FCAST_DIR + yr + S;
		SubductionConverter converter = SubductionConverter.create(log);
		for (SourceFile file : files) {
			checkArgument(file.type == INTERFACE, "Wrong file type: %s", file.type.name());
			converter.convert(file, out);
		}
	}
	
	static void convertCluster(List<SourceFile> files, String yr, Logger log) {
		String out = FCAST_DIR + yr + S;
		ClusterConverter converter = ClusterConverter.create(log);
		for (SourceFile file : files) {
			checkArgument(file.type == CLUSTER, "Wrong file type: %s", file.type.name());
			converter.convert(file, out, yr.equals("2014") ? MGR_2014 : MGR_2008);
		}
	}
	

	
	static Logger createLogger(Class<?> clazz, String name, SourceRegion region, String yr) {
		String time = sdf.format(new Date());
		String loggerID = clazz.getName() + "-" + region.name() + "-" + yr;
		String logName = LOG_DIR + name + "-" + yr + "-" + region.name() + "-" + time + ".log";
		return Utils.logger(loggerID, logName, LEVEL);
	}
}
