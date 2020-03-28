package ijt.plugins;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Frame;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.text.TextPanel;
import ij.text.TextWindow;

import ijt.table.IJUtils;


/**
 * @author dlegland
 *
 */
public class OverlayValues implements PlugIn, DialogListener 
{
    ImagePlus labelImagePlus;
    
    ImagePlus resultPlus;
    
    ResultsTable table = null;
    
    GenericDialog gd = null;
    
    String selectedHeaderName = null;
    int xCoordColumnIndex = 0;
    int yCoordColumnIndex = 0;
    int valueColumnIndex = 0;

    
    /* (non-Javadoc)
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
    @Override
    public void run(String arg)
    {
        // Work on current image, and exit if no one is open
        this.labelImagePlus = IJ.getImage();
    
        // Get the list of windows containing tables
        ArrayList<TextWindow> textWindows = IJUtils.getTableWindows();
        if (textWindows.size() == 0)
        {
            IJ.error("Requires at least one Table window");
            return;
        }

        createDialog();
        gd.showDialog();
        if (gd.wasCanceled())
        {
            return;
        }
        parseDialogOptions();

        
        // Extract coordinates and value to display
        double[] xCoords = table.getColumnAsDoubles(xCoordColumnIndex); 
        double[] yCoords = table.getColumnAsDoubles(yCoordColumnIndex); 
        double[] values = table.getColumnAsDoubles(valueColumnIndex);
        
        // convert coords to point array
        Point2D[] coords = new Point2D[xCoords.length];
        for (int i = 0; i < xCoords.length; i++)
        {
            coords[i] = new Point2D.Double(xCoords[i], yCoords[i]);
        }
        
        // show overaly
        showResultsAsOverlay(coords, values, labelImagePlus);
    }

    private GenericDialog createDialog()
    {
        // Get the list of windows containing tables
        ArrayList<TextWindow> textWindows = IJUtils.getTableWindows();
        if (textWindows.size() == 0)
        {
            IJ.error("Requires at least one Table window");
            return null;
        }
        
        // create the list of tables
        String[] tableNames = new String[textWindows.size()];
        for (int i = 0; i < textWindows.size(); i++) 
        {
            tableNames[i] = textWindows.get(i).getTitle();
        }
        
        // Choose current table
        TextPanel tp = textWindows.get(0).getTextPanel();
        ResultsTable table = tp.getResultsTable();
        this.table = table;
        
        // Choose current heading
        String[] headings = getColumnHeadings(table);
        String defaultHeading = headings[0];
        for (String hdg : headings) IJ.log("  " + hdg);

        // create the list of image names
        int[] indices = WindowManager.getIDList();
        String[] imageNames = new String[indices.length];
        for (int i=0; i<indices.length; i++)
        {
            imageNames[i] = WindowManager.getImage(indices[i]).getTitle();
        }
        
        // name of selected image
        String selectedImageName = IJ.getImage().getTitle();

        
        // Create Dialog
        this.gd = new GenericDialog("Overlay Values");
        gd.addChoice("Results Table:", tableNames, tableNames[0]);
        gd.addChoice("X Column:", headings, defaultHeading);
        gd.addChoice("Y Column:", headings, defaultHeading);
        gd.addChoice("Value Column:", headings, defaultHeading);
        gd.addChoice("Image To Overlay:", imageNames, selectedImageName);
        gd.addDialogListener(this);
        
        return gd;
    }
    
    private String[] getColumnHeadings(ResultsTable table)
    {
        int nc = table.getLastColumn()+1;
        String[] headings = new String[nc];
        for (int c = 0; c < nc; c++)
        {
            headings[c] = table.getColumnHeading(c);
        }
        return headings;
    }
    
    /**
     * Analyzes dialog, and setup inner fields of the class.
     */
    private void parseDialogOptions() 
    {
        String tableName = this.gd.getNextChoice();
        Frame tableFrame = WindowManager.getFrame(tableName);
        this.table = ((TextWindow) tableFrame).getTextPanel().getResultsTable();
        
        this.xCoordColumnIndex = this.gd.getNextChoiceIndex();
        this.yCoordColumnIndex = this.gd.getNextChoiceIndex();
        this.valueColumnIndex  = this.gd.getNextChoiceIndex();
                
        int imageIndex  = this.gd.getNextChoiceIndex();
        IJ.log("image index: " + imageIndex);
        this.labelImagePlus = WindowManager.getImage(imageIndex + 1);
        IJ.log("label image name: " + labelImagePlus.getTitle());
        
    }
    
    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent evt) 
    {
        if (gd.wasCanceled() || gd.wasOKed()) 
        {
            return true;
        }

        @SuppressWarnings("rawtypes")
        Vector choices = gd.getChoices();
        if (choices == null) 
        {
            IJ.log("empty choices array...");
            return false;
        }
        
        // Change of the data table
        if (evt.getSource() == choices.get(0)) 
        {
            String tableName = ((Choice) evt.getSource()).getSelectedItem();
            Frame tableFrame = WindowManager.getFrame(tableName);
            this.table = ((TextWindow) tableFrame).getTextPanel().getResultsTable();
            
            // Choose column headings
            String[] headings = getColumnHeadings(table);
            String defaultHeading = headings[0];
            
            // Update headings for xcoord, ycoord and value choices
            setChoiceStrings((Choice) choices.get(1), headings, defaultHeading);
            setChoiceStrings((Choice) choices.get(2), headings, defaultHeading);
            setChoiceStrings((Choice) choices.get(3), headings, defaultHeading);
        }
        
        return true;
    }
    
    private void setChoiceStrings(Choice choice, String[] strings,
            String defaultString)
    {
        choice.removeAll();
        for (String str : strings)
        {
            choice.add(str);
        }
        choice.select(defaultString);
    }
    
    /**
     * Display the result of text value as overlay on a given image.
     * 
     * @param target
     *            the ImagePlus used to display result
     * @param table
     *            the ResultsTable containing columns "xi", "yi" and "Radius"
     * @param the
     *            resolution in each direction
     */
    private void showResultsAsOverlay(Point2D[] coords, double[] values, ImagePlus target) 
    {
        // get spatial calibration of target image
        Calibration calib = target.getCalibration();

        // create overlay
        Overlay overlay = new Overlay();
        Roi roi;
        
        // add each circle to the overlay
        for (int i = 0; i < coords.length; i++)
        {
            Point2D coord = coords[i];
            Point2D pos = uncalibrate(coord, calib);
            double xi = pos.getX();
            double yi = pos.getY();
            double vi = values[i];
            
            String str = String.format("%5.2f", vi);
            roi = new TextRoi((int) xi, (int) yi, str);
            roi.setStrokeColor(Color.BLUE);
            overlay.add(roi);
        }
        
        target.setOverlay(overlay);
    }
    
    /**
     * Determines the point corresponding to the uncalibrated version of this
     * point, assuming it was defined in calibrated coordinates.
     * 
     * @param point
     *            the point in calibrated coordinates
     * @param calib
     *            the spatial calibration to consider
     * @return the circle in pixel coordinates
     */
    private final static Point2D uncalibrate(Point2D point, Calibration calib)
    {
        double x2 = (point.getX() - calib.xOrigin) / calib.pixelWidth;
        double y2 = (point.getY() - calib.yOrigin) / calib.pixelHeight;
        return new Point2D.Double(x2, y2);
    }
    
}
