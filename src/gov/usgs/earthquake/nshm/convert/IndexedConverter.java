package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.convert.IndexedFaultConverter.SECTION_XML_IN;
import static gov.usgs.earthquake.nshm.convert.IndexedFaultConverter.cleanName;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class IndexedConverter {

	private static final String SOL_DIR = "../../svn/OpenSHA/tmp/UC33/src/bravg/FM/";
	private static final String FM31_SOL = "UC33brAvg_FM31";
	private static final String FM32_SOL = "UC33brAvg_FM32";
	private static final String OUT_DIR = "forecasts/TestUC3/Indexed Fault/";

	public static void main(String[] args) throws Exception {
		double weight = 1.0;
		
		// convert branch averaged solution fault and grid sources
		 IndexedFaultConverter.processSolution(SOL_DIR, FM31_SOL, OUT_DIR, weight);
		 IndexedGridConverter.processGridFile(SOL_DIR, FM31_SOL, OUT_DIR, weight);

		 IndexedFaultConverter.processSolution(SOL_DIR, FM32_SOL, OUT_DIR, weight);
		 IndexedGridConverter.processGridFile(SOL_DIR, FM32_SOL, OUT_DIR, weight);

//		compareFaultModelIndices();
	}
	
	/*
	 * 
	 */

	/*
	 * Combining UC3 fault model branch averaged solutions:
	 * 
	 * Any FaultModel 3.1 solution will map to its original indices.
	 * 
	 * Those sections in FaultModel 3.2 that are replicated in 3.1 will be
	 * mapped to their 3.1 counterpart index.
	 * 
	 * Those sections in FaultModel 3.2 but not in 3.1 will be appended
	 * to the FaultModel 3.1 section list and their indices mapped
	 * to their new indices in the master list.
	 * 
	 * NOTE becasue inversions were run separately for each fualt model,
	 * need to consider that even though section participation may be same
	 * for two ruptures, mags, rates and other properties etc. may not be
	 * 
	 */

	static void compareFaultModelIndices() throws Exception {

		BiMap<Integer, String> fm31map = readSections(SOL_DIR, FM31_SOL);
		BiMap<Integer, String> fm32map = readSections(SOL_DIR, FM32_SOL);
		
		int count = 0;
		
		// list FM31 entires with FM32 indices, or lack thereof
		for (Entry<Integer, String> entry : fm31map.entrySet()) {
			String masterIdxStr = toString(count);
			String flag31 = "FM31  ";
			String idx31str = toString(entry.getKey());
			Integer idx32 = fm32map.inverse().get(entry.getValue());
			String flag32 = (idx32 != null) ? "FM32  " : "--    ";
			String idx32str = (idx32 != null) ? toString(idx32) : "--    ";
			System.out.println(masterIdxStr + flag31 + idx31str + flag32 + idx32str + entry.getValue());
			count++;
		}
		
		// append FM32 entries missing from FM31
		for (Entry<Integer, String> entry : fm32map.entrySet()) {
			if (fm31map.containsValue(entry.getValue())) continue;
			String masterIdxStr = toString(count);
			String flag31 = "--    ";
			String idx31str =  "--    ";
			String flag32 = "FM32  ";
			String idx32str = toString(entry.getKey());
			System.out.println(masterIdxStr + flag31 + idx31str + flag32 + idx32str + entry.getValue());
			count++;
		}
		
	}
	
	private static String toString(int idx) {
		return Strings.padEnd(Integer.toString(idx), 6, ' ');
	}


	private static BiMap<Integer, String> readSections(String solDirPath, String sol)
			throws ParserConfigurationException, SAXException, IOException {

		ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(zip.getInputStream(sectionsEntry));
		docIn.getDocumentElement().normalize();
		
		zip.close();

		// file in
		Element rootIn = docIn.getDocumentElement();
		NodeList sectsIn = ((Element) rootIn.getElementsByTagName("FaultSectionPrefDataList").item(
			0)).getChildNodes();

		ImmutableBiMap.Builder<Integer, String> builder = ImmutableBiMap.builder();
		for (int i = 0; i < sectsIn.getLength(); i++) {
			Node node = sectsIn.item(i);
			if (!(node instanceof Element)) continue;
			Element sectIn = (Element) node;

			String name = cleanName(sectIn.getAttribute("sectionName"));
			String indexStr = sectIn.getAttribute("sectionId");
			int index = Integer.valueOf(indexStr);

			builder.put(index, name);
		}
		return builder.build();
	}

}
