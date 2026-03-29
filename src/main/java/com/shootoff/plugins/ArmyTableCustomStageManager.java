package com.shootoff.plugins;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ArmyTableCustomStageManager {

    private static final String FILE_PATH = "exercises/army_table_vi_custom.txt";

    public static void saveEngagements(List<ArmyTableVIQualification.Engagement> engagements) throws IOException {
        File f = new File(FILE_PATH);
        if(!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(f))) {
            for (ArmyTableVIQualification.Engagement e : engagements) {
                out.println("ENGAGEMENT," + e.number + "," + e.position.name() + "," + e.delayBeforeSec + "," + e.exposureSec);
                for (ArmyTableVIQualification.TargetSpec t : e.targets) {
                    out.println("TARGET," + t.distanceMeters + "," + (t.side == null ? "" : t.side));
                }
            }
        }
    }

    public static List<ArmyTableVIQualification.Engagement> loadEngagements() throws IOException {
        File f = new File(FILE_PATH);
        if (!f.exists()) return null;

        List<ArmyTableVIQualification.Engagement> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            ArmyTableVIQualification.Engagement current = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts[0].equals("ENGAGEMENT")) {
                    current = new ArmyTableVIQualification.Engagement(
                        Integer.parseInt(parts[1]),
                        ArmyTableVIQualification.Position.valueOf(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4])
                    );
                    list.add(current);
                } else if (parts[0].equals("TARGET") && current != null) {
                    current.targets.add(new ArmyTableVIQualification.TargetSpec(
                        Integer.parseInt(parts[1]),
                        parts.length > 2 ? parts[2] : ""
                    ));
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to parse custom army table engagements: " + ex.getMessage());
            return null; // Fallback to default if parsing totally fails
        }
        return list;
    }
}
