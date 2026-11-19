package app.locaport.files.ui;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.core.content.ContextCompat;

import app.locaport.files.R;
import app.locaport.files.server.LocaportService;

public class LocaportTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (LocaportService.isRunning()) {
            stopService(new android.content.Intent(this, LocaportService.class)
                    .setAction(LocaportService.ACTION_STOP));
        } else {
            ContextCompat.startForegroundService(this,
                    new android.content.Intent(this, LocaportService.class)
                            .setAction(LocaportService.ACTION_START));
        }
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(LocaportService.isRunning() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.tile_label));
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.updateTile();
    }
}
