package org.maprva.wikidata;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.util.Downloader;
import org.maprva.wikidata.feature.QrankDb;
import org.maprva.wikidata.layers.Pois;
import java.nio.file.Path;
import org.locationtech.jts.geom.Envelope;


public class Wikidata extends ForwardingProfile {

  public Wikidata(QrankDb qrankDb) {
    var poi = new Pois(qrankDb);
    registerHandler(poi);
    registerSourceHandler("osm", poi);
  }

  @Override
  public String name() {
    return "Wikidata";
  }

  @Override
  public String description() {
    return "Wikidata layers derived from OpenStreetMap and Wikidata";
  }

  @Override
  public boolean isOverlay() {
    return false;
  }

  @Override
  public String attribution() {
    return """
      <a href="https://www.openstreetmap.org/copyright" target="_blank">&copy; OpenStreetMap contributors</a>
      """.trim();
  }

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws Exception {
    args = args.orElse(Arguments.of("maxzoom", 15));

    Path dataDir = Path.of("data");
    Path sourcesDir = dataDir.resolve("sources");

    String area = args.getString("area", "geofabrik area to download", "monaco");

    var planetiler = Planetiler.create(args)
      .addOsmSource("osm", Path.of("data", "sources", area + ".osm.pbf"), "geofabrik:" + area);

    Downloader.create(planetiler.config())
      .add("qrank", "https://qrank.wmcloud.org/download/qrank.csv.gz", sourcesDir.resolve("qrank.csv.gz")).run();

    var qrankDb = QrankDb.fromCsv(sourcesDir.resolve("qrank.csv.gz"));

    planetiler.setProfile(new Wikidata(qrankDb)).setOutput(Path.of(area + ".pmtiles"))
      .run();
  }
}
