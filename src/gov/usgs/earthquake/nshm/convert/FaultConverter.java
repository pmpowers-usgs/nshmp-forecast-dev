package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CA;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CEUS;
import static org.opensha.eq.forecast.SourceElement.FAULT_SOURCE;
import static org.opensha.eq.forecast.SourceElement.FAULT_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.GEOMETRY;
import static org.opensha.eq.forecast.SourceElement.TRACE;
import static org.opensha.util.Parsing.addElement;
import static org.opensha.util.Parsing.readInt;
import static org.opensha.util.Parsing.stripComment;
import static org.opensha.util.Parsing.toDoubleList;

import gov.usgs.earthquake.nshm.util.MFD_Type;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.eq.Magnitudes;
import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.forecast.MagUncertainty;
import org.opensha.geo.GeoTools;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.mfd.MFDs;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class FaultConverter {

	private static Logger log;
	private static Level level =  Level.INFO;
	
	static {
		log = Utils.logger("tmp/logs/2008parser.log");
		log.setLevel(level);
		for (Handler h : log.getHandlers()) {
			h.setLevel(level);
		}
	}
	
	private FaultConverter() {}
	
	static void convert(SourceFile sf, String dir) {
		
		try {
			log.info("Source file: " + sf.name + " " + sf.region + " " + sf.weight);
			Exporter export = new Exporter();
			export.name = sf.name;	
			export.weight = sf.weight;
	
			// KLUDGY nameIdx indicates the array index at which a fault
			// name begins; most NSHMP files define the fault name on a line such as:
			//
			//  2 3 1 1    805 Juniper Mountain fault
			//
			// where the starting index would be 4. (The identifying number is
			// necessary to distinguish some faults, e.g. Seattle Fault in orwa_c.in)
			// CA files generally start at idx=3; general WUS case is 4
			int nameIdx = (sf.region == CA || sf.region == CEUS) ? 3 : 
						  (sf.name.equals("wasatch.3dip.74.in")) ? 4 : 5;
	
			Iterator<String> lines = sf.lineIterator();
	
			// skip irrelevant header data
			skipSiteData(lines);
			lines.next(); // rMax and discretization
			skipGMMs(lines);
			lines.next(); // distance sampling on fault and dMove

			// load magnitude uncertainty data
			export.magDat = readMagUncertainty(Parsing.toLineList(lines, 4));
			if (log.isLoggable(Level.INFO)) {
				log.info(export.magDat.toString());
			}
	
			while (lines.hasNext()) {
				
				// collect data on source name line
				SourceData fDat = new SourceData();
				List<String> srcInfo = Parsing.toStringList(lines.next());
				MFD_Type mfdType = MFD_Type.typeForID(Integer.valueOf(srcInfo.get(0)));
				fDat.file = sf;
				fDat.focalMech = Utils.typeForID(Integer.valueOf(srcInfo.get(1)));
				fDat.nMag = Integer.valueOf(srcInfo.get(2));
				fDat.name = cleanName(Joiner.on(' ').join(
					Iterables.skip(srcInfo, nameIdx)));
	
				// read source magnitude data and build mfds; due to peculiarities
				// of how mfd's are handled with different uncertainty settings
				// under certain conditions (e.g. when mMax < 6.5), cloned magDat
				// are used so that any changes to magDat do not percolate to
				// other sources TODO these peculiarities are handled when sources
				// are reconstituted so this cloning probably isn't necessary
				List<String> mfdDat = Parsing.toLineList(lines, fDat.nMag);
				read_MFDs(fDat, mfdType, mfdDat, export.magDat);
				
				readTrace(lines, fDat);
				
				// append dip to name if normal (NSHMP 3dip) 
				if (fDat.focalMech == FocalMech.NORMAL) {
					fDat.name += " " + ((int) fDat.dip);
				}
				if (fDat.mfds.size() == 0) {
					log.severe("Source with no mfds");
					System.exit(1);
				}
				if (export.names.contains(fDat.name)) {
					log.warning("Name set already contains: " + fDat.name);
					// list multimap handles ordered preservation of multiple
					// faultData for a given name
					// TODO these need to be reduced
				}
				export.names.add(fDat.name);
				export.map.put(fDat.name, fDat);
			}
			
			// KLUDGY: this should be handled now that a Set of names is used
			// in FaultSourceData, however we want to be aware of potential
			// duplicates so we now log the addition of existing names to 
			// the name set.
			// if (fName.contains("3dip")) cleanStrikeSlip(srcList);
			
			String S = File.separator;
			String outPath = dir + S + sf.region + S + sf.type + S + 
					sf.name.substring(0, sf.name.lastIndexOf('.')) + ".xml";
			File outFile = new File(outPath);
			Files.createParentDirs(outFile);
			export.writeXML(new File(outPath));
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Fault parse error: exiting", e);
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static MagUncertainty readMagUncertainty(List<String> src) {
		
		// epistemic
		int epiCount = readInt(src.get(0), 0);
		double[] epiDeltas = Doubles.toArray(toDoubleList(src.get(1)));
		double[] epiWeights = Doubles.toArray(toDoubleList(src.get(2)));
		double epiCutoff = 6.5;
		
		// aleatory
		List<Double> aleatoryMagDat = toDoubleList(stripComment(src.get(3), '!'));
		double aleatorySigmaTmp = aleatoryMagDat.get(0);
		boolean moBalance = aleatorySigmaTmp > 0.0;
		double aleaSigma = Math.abs(aleatorySigmaTmp);
		int aleaCount = aleatoryMagDat.get(1).intValue() * 2 + 1;
		double aleaCutoff = 6.5;
		
		return MagUncertainty.create(epiDeltas, epiWeights, epiCutoff,
			aleaSigma, aleaCount, moBalance, aleaCutoff);
	}

	
	private static void read_MFDs(SourceData fd, MFD_Type type, 
			List<String> lines, MagUncertainty md) {
		switch (type) {
			case CH:
				read_CH(lines, fd);
				break;
			case GR:
				read_GR(lines, fd);
				break;
			case GRB0:
				read_GRB0(lines, fd, md);
				break;
		}
	}

	private static void read_CH(List<String> lines, SourceData fd) {
		boolean floats = false;
		for (String line : lines) {
			CH_Data ch = CH_Data.create(
				Parsing.readDouble(line, 0),
				Parsing.readDouble(line, 1),
				Parsing.readDouble(line, 2));
			ch.floats = floats;
			fd.mfds.add(ch);
			log(fd, MFD_Type.CH, floats);
		}
	}

	private static void read_GR(List<String> lines, SourceData fd) {
		boolean floats = true;
		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			GR_Data gr = GR_Data.createForFault(line, fd, log);
			gr.floats = floats;
			grData.add(gr);
		}

		// build all GR_Data
		for (GR_Data gr : grData) {
			if (gr.nMag > 1) {
				fd.mfds.add(gr);
				log(fd, MFD_Type.GR, floats);
			} else {
				CH_Data ch = CH_Data.create(gr.mMin, MFDs.grRate(gr.aVal,
					gr.bVal, gr.mMin), gr.weight);
				ch.floats = floats;
				fd.mfds.add(ch);
				log(fd, MFD_Type.CH, floats);
			}
		}

	}

	private static void read_GRB0(List<String> lines, SourceData fd, MagUncertainty md) {
		boolean floats = true;
		
		checkArgument(!md.hasAleatory(),
			"Aleatory unc. is incompatible with GR b=0 branches");

		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			GR_Data gr = GR_Data.createForFault(line, fd, log);
			gr.floats = floats;
			checkArgument(gr.mMax > gr.mMin,
				"GR b=0 branch can't handle floating CH (mMin=mMax)");
			grData.add(gr);
			
		}

		for (GR_Data gr : grData) {
			gr.weight *= 0.5;
			fd.mfds.add(gr);
			log(fd, MFD_Type.GR, floats);
			
			// adjust for b=0, preserving cumulative moment rate
			double tmr = Utils.totalMoRate(gr.mMin, gr.nMag, gr.dMag, gr.aVal, gr.bVal);
			double tsm = Utils.totalMoRate(gr.mMin, gr.nMag, gr.dMag, 0, 0);
			GR_Data grB0 = GR_Data.copyOf(gr);
			grB0.aVal = Math.log10(tmr / tsm);
			grB0.bVal = 0;
			fd.mfds.add(grB0);
			log(fd, MFD_Type.GRB0, floats);
		} 
	}
	 
	private static void log(SourceData fd, MFD_Type mfdType, boolean floats) {
		String mfdStr = Strings.padEnd(mfdType.name(), 5, ' ') +
			(floats ? "f " : "  ");
		log.info(mfdStr + fd.name);
	}
	
	private static void readTrace(Iterator<String> it, SourceData fd) { //, Logger log) {
		readFaultGeom(it.next(), fd);

		int traceCount = Parsing.readInt(it.next(), 0);
		List<String> traceDat = Parsing.toLineList(it, traceCount);
		List<Location> locs = Lists.newArrayList();
		for (String ptDat : traceDat) {
			List<Double> latlon = Parsing.toDoubleList(ptDat);
			locs.add(Location.create(latlon.get(0), latlon.get(1), fd.top));
		}
		fd.locs = LocationList.create(locs);
		
		// catch negative dips; kludge in configs
		// used instead of reversing trace
		if (fd.dip < 0) {
			fd.dip = -fd.dip;
			fd.locs = LocationList.reverseOf(fd.locs);
		}
	}
	
	private static void readFaultGeom(String line, SourceData fd) {
		List<Double> fltDat = Parsing.toDoubleList(line);
		fd.dip = fltDat.get(0);
		fd.width = fltDat.get(1);
		fd.top = fltDat.get(2);
	}

	private static void skipSiteData(Iterator<String> it) {
		int numSta = Parsing.readInt(it.next(), 0); // grid of sites or station list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30) and Campbell basin depth
	}
	
	private static void skipGMMs(Iterator<String> it) {
		int nP = Parsing.readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			double epi = Parsing.readDouble(it.next(), 1); // period w/ gm epi. flag
			if (epi > 0) Iterators.advance(it, 3); 
			it.next(); // out file
			it.next(); // num ground motion values
			it.next(); // ground motion values
			int nAR = Parsing.readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
		}
	}
	
	private static String cleanName(String name) {
		return CharMatcher.WHITESPACE.collapseFrom(name
			.replace("faults", "")
			.replace("fault", "")
			.replace("zone", "")
			.replace("-", " - ")
			.replace("/", " - ")
			.replace(" , ", " - ")
			.replace(", ", " - ")
			.replace(";", " : "),
				 ' ').trim();
	}
	
	/* Wrapper class for individual sources */
	static class SourceData {
		SourceFile file;
		List<MFD_Data> mfds = Lists.newArrayList();
		FocalMech focalMech;
		int nMag;
		String name;
		LocationList locs;
		double dip;
		double width;
		double top;
		
		boolean equals(SourceData in) {
			return file.name.equals(in.file.name) &&
					focalMech == in.focalMech &&
					name.equals(in.name) &&
					locs.equals(in.locs) &&
					dip == in.dip &&
					width == in.width &&
					top == in.top;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(name);
			sb.append(" mech=" + focalMech);
			sb.append(" dip=" + dip );
			sb.append(" width=" + width);
			sb.append(" top=" + top);
			sb.append(locs);
			return sb.toString();
		}
	}
	
	static class Exporter {
		
		String name = "Unnamed Source Set";
		double weight = 1.0;
		Set<String> names = Sets.newLinkedHashSet();
		ListMultimap<String, FaultConverter.SourceData> map = ArrayListMultimap.create();
		MagUncertainty magDat;

		public void writeXML(File out) throws ParserConfigurationException,
				TransformerException {

			DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			// root elements
			Document doc = docBuilder.newDocument();
			Element root = doc.createElement(FAULT_SOURCE_SET.toString());
			doc.appendChild(root);
			root.setAttribute("name", name);
			root.setAttribute("weight", Double.toString(weight));

			// defaults and uncertainty
			magDat.appendTo(root);

			for (String name : names) {
				Element src = addElement(FAULT_SOURCE, root);
				src.setAttribute("name", name);

				List<FaultConverter.SourceData> fDatList = map.get(name);
				// first test that all non-MFD data of multi entries are equal
				if (fDatList.size() > 1) {
					FaultConverter.SourceData first = fDatList.get(0);

					for (int i = 1; i < fDatList.size(); i++) {
						if (!first.equals(fDatList.get(i))) {
							throw new IllegalStateException(
								LINE_SEPARATOR.value() + name + " in " +
									first.file.name +
									" has multiple dissimilar entries" +
									LINE_SEPARATOR.value() + "index = 0 :: " +
									first + LINE_SEPARATOR.value() +
									"index = " + i + " :: " + fDatList.get(i));
						}
					}
				}
				// consolidate MFDs at beginning of element
				for (FaultConverter.SourceData fDat : fDatList) {
					// MFDs
					for (MFD_Data mfdDat : fDat.mfds) {
						mfdDat.appendTo(src);
					}
				}
				// append geometry from first entry
				FaultConverter.SourceData first = fDatList.get(0);
				Element geom = addElement(GEOMETRY, src);
				geom.setAttribute("dip", Double.toString(first.dip));
				geom.setAttribute("width", Double.toString(first.width));
				geom.setAttribute("rake",
					Double.toString(first.focalMech.rake()));
				// geom.setAttribute("mech", first.focalMech.name());
				Element trace = addElement(TRACE, geom);
				trace.setTextContent(first.locs.toString());
			}

			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
			Transformer trans = transformerFactory.newTransformer();
			trans.setOutputProperty(OutputKeys.INDENT, "yes");
			trans.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "2");
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(out);

			// Output to console for testing
			// StreamResult result = new StreamResult(System.out);

			trans.transform(source, result);
		}
	}

}
