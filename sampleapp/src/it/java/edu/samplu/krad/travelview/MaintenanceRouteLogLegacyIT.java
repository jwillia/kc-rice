package edu.samplu.krad.travelview;

import edu.samplu.common.KradMenuLegacyITBase;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Kuali Rice Team (rice.collab@kuali.org)
 */

public class MaintenanceRouteLogLegacyIT extends KradMenuLegacyITBase {
    @Override
    protected String getLinkLocator() {
        return "a[Travel Account Maintenance (New)]";
    }

    @Ignore // need to figure out css locators
    /**
     * Verify the Route Log section exists and contains an IFrame
     */
    public void testVerifyRouteLog() throws Exception {
        gotoMenuLinkLocator();
        waitAndClick("css=span:contains('Route Log')");
        waitForElementPresent("//iframe[contains(@src,'RouteLog.do')]");
    }
}
