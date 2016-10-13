package swp;

import java.io.*;
import java.util.*;

/**
 * Created by parttimenerd on 28.09.16.
 */
public class Config {

	public static final String configFile = "config.ini";

	private static final Map<String, String> config = new HashMap<String, String>(){{
		put("useLALR", "yes");
		put("tmpDir", "/tmp");
		put("cacheInFile", "yes");
	}};

	/** Use LARL instead of LR? */
	public static boolean useLALR(){
		return config.get("useLALR").equals("yes");
	}

	public static String getTmpDir(){
		return config.get("tmpDir");
	}

	public static boolean cacheInFile(){
		return config.get("cacheInFile").equals("yes");
	}

	private static void loadConfig(){
		try {
			boolean rewriteConfigFile = false;
			File file = new File(configFile);
			if (file.exists()){
				Set<String> keys = config.keySet();
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine()) != null){
					if (line.contains(" = ")){
						String[] parts = line.split(" = ");
						if (config.containsKey(parts[0])){
							keys.remove(parts[0]);
							config.put(parts[0], parts[1]);
						} else {
							System.err.println("Unknown config key \"" + parts[0] + "\"");
						}
					}
				}
				rewriteConfigFile = !keys.isEmpty();
				reader.close();
			} else {
				rewriteConfigFile = true;
			}
			if (rewriteConfigFile){
				BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
				String[] keys = config.keySet().toArray(new String[0]);
				Arrays.sort(keys);
				for (String key : keys){
					writer.write(String.format("%s = %s\n", key, config.get(key)));
				}
				writer.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static {
		loadConfig();
	}
}