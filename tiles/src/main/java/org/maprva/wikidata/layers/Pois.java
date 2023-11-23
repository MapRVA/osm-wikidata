package org.maprva.wikidata.layers;

import static com.onthegomap.planetiler.util.Parse.parseDoubleOrNull;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.collection.FeatureGroup;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.ZoomFunction;
import org.maprva.wikidata.feature.FeatureId;
import org.maprva.wikidata.feature.QrankDb;
import java.util.List;

public class Pois implements ForwardingProfile.FeatureProcessor, ForwardingProfile.FeaturePostProcessor {

  private QrankDb qrankDb;

  public Pois(QrankDb qrankDb) {
    this.qrankDb = qrankDb;
  }

  @Override
  public String name() {
    return "pois";
  }

  private static final double WORLD_AREA_FOR_70K_SQUARE_METERS =
    Math.pow(GeoUtils.metersToPixelAtEquator(0, Math.sqrt(70_000)) / 256d, 2);
  private static final double LOG2 = Math.log(2);

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector features) {
    if (!sf.hasTag("wikidata")) {
      return;
    }
    if (!(
          sf.hasTag("tourism") && !sf.hasTag("tourism", "no")
          ||
          sf.hasTag("heritage") && !sf.hasTag("heritage", "no")
          ||
          sf.hasTag("historic") && !sf.hasTag("historic", "no")
          ||
          sf.hasTag("building") && !sf.hasTag("building", "no")
         )
       ) {
     return;
    }

    String wikidata = sf.getString("wikidata");
    long qrank = -1;
    if (wikidata != null) {
      qrank = qrankDb.get(wikidata);
    }
    int minZoom = 13;
    if (qrank > 1_000_000) {
      minZoom = 3;
    } else if (qrank > 100_000) {
      minZoom = 5;
    } else if (qrank > 10_000) {
      minZoom = 7;
    } else if (qrank > 1_000) {
      minZoom = 9;
    } else if (qrank > 1000) {
      minZoom = 11;
    }
    FeatureCollector.Feature feat;
    if (sf.canBePolygon() || sf.canBeLine()) {
      feat = features.pointOnSurface(this.name());
    } else {
      feat = features.point(this.name());
    }

    feat
      // all POIs should receive their IDs at all zooms
      // (there is no merging of POIs like with lines and polygons in other layers)
      .setId(FeatureId.create(sf))
      // Core OSM tags for different kinds of places
      .setAttr("qrank", qrank)
      .setAttr("wikidata", sf.getString("wikidata"))
      .setAttr("osm_node_id", sf.id())
      .setZoomRange(0, 15)
      .setBufferPixels(128);

    var qRankToSortKeyRatio = ((float) (FeatureGroup.SORT_KEY_MAX-FeatureGroup.SORT_KEY_MIN))/((float) (this.qrankDb.max));
    feat.setSortKeyDescending((int) (qRankToSortKeyRatio*qrank - FeatureGroup.SORT_KEY_MIN));

    if (sf.hasTag("name")) {
      feat.setAttr("name", sf.getString("name"));
    }
    // Even with the categorical zoom bucketing above, we end up with too dense a point feature spread in downtown
    // areas, so cull the labels which wouldn't label at earlier zooms than the max_zoom of 15
    feat.setPointLabelGridSizeAndLimit(14, 10, 1);

    // and also whenever you set a label grid size limit, make sure you increase the buffer size so no
    // label grid squares will be the consistent between adjacent tiles
    feat.setBufferPixelOverrides(ZoomFunction.maxZoom(14, 32));
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {

    // TODO: (nvkelso 20230623) Consider adding a "pmap:rank" here for POIs, like for Places

    return items;
  }
}
