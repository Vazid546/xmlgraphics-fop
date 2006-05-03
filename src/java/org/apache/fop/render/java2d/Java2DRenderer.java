/*
 * Copyright 1999-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.java2d;

// Java
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.area.CTM;
import org.apache.fop.area.PageViewport;
import org.apache.fop.area.Trait;
import org.apache.fop.area.inline.ForeignObject;
import org.apache.fop.area.inline.Image;
import org.apache.fop.area.inline.Leader;
import org.apache.fop.area.inline.TextArea;
import org.apache.fop.fo.Constants;
import org.apache.fop.fonts.Font;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.image.FopImage;
import org.apache.fop.image.ImageFactory;
import org.apache.fop.image.XMLImage;
import org.apache.fop.render.AbstractPathOrientedRenderer;
import org.apache.fop.render.Graphics2DAdapter;
import org.apache.fop.render.RendererContext;
import org.apache.fop.render.pdf.CTMHelper;
import org.apache.fop.render.pdf.PDFRendererContextConstants;

/**
 * The <code>Java2DRenderer</code> class provides the abstract technical
 * foundation for all rendering with the Java2D API. Renderers like
 * <code>AWTRenderer</code> subclass it and provide the concrete output paths.
 * <p>
 * A lot of the logic is performed by <code>AbstractRenderer</code>. The
 * class-variables <code>currentIPPosition</code> and
 * <code>currentBPPosition</code> hold the position of the currently rendered
 * area.
 * <p>
 * <code>Java2DGraphicsState state</code> holds the <code>Graphics2D</code>,
 * which is used along the whole rendering. <code>state</code> also acts as a
 * stack (<code>state.push()</code> and <code>state.pop()</code>).
 * <p>
 * The rendering process is basically always the same:
 * <p>
 * <code>void renderXXXXX(Area area) {
 *    //calculate the currentPosition
 *    state.updateFont(name, size, null);
 *    state.updateColor(ct, false, null);
 *    state.getGraph.draw(new Shape(args));
 * }</code>
 *
 */
public abstract class Java2DRenderer extends AbstractPathOrientedRenderer implements Printable {

    /** The scale factor for the image size, values: ]0 ; 1] */
    protected double scaleFactor = 1;

    /** The page width in pixels */
    protected int pageWidth = 0;

    /** The page height in pixels */
    protected int pageHeight = 0;

    /** List of Viewports */
    protected List pageViewportList = new java.util.ArrayList();

    /** The 0-based current page number */
    private int currentPageNumber = 0;

    /** The 0-based total number of rendered pages */
    private int numberOfPages;

    /** true if antialiasing is set */
    protected boolean antialiasing = true;

    /** true if qualityRendering is set */
    protected boolean qualityRendering = true;

    /** The current state, holds a Graphics2D and its context */
    protected Java2DGraphicsState state;

    /** true if the renderer has finished rendering all the pages */
    private boolean renderingDone;

    private GeneralPath currentPath = null;
    
    /** Default constructor */
    public Java2DRenderer() {
    }

    /**
     * @see org.apache.fop.render.Renderer#setUserAgent(org.apache.fop.apps.FOUserAgent)
     */
    public void setUserAgent(FOUserAgent foUserAgent) {
        super.setUserAgent(foUserAgent);
        userAgent.setRendererOverride(this); // for document regeneration
    }

    /** @return the FOUserAgent */
    public FOUserAgent getUserAgent() {
        return userAgent;
    }

    /**
     * @see org.apache.fop.render.Renderer#setupFontInfo(org.apache.fop.fonts.FontInfo)
     */
    public void setupFontInfo(FontInfo inFontInfo) {
        //Don't call super.setupFontInfo() here! Java2D needs a special font setup
        // create a temp Image to test font metrics on
        fontInfo = inFontInfo;
        BufferedImage fontImage = new BufferedImage(100, 100,
                BufferedImage.TYPE_INT_RGB);
        FontSetup.setup(fontInfo, fontImage.createGraphics());
    }

    /** @see org.apache.fop.render.Renderer#getGraphics2DAdapter() */
    public Graphics2DAdapter getGraphics2DAdapter() {
        return new Java2DGraphics2DAdapter(state);
    }

    /**
     * Sets the new scale factor.
     * @param newScaleFactor ]0 ; 1]
     */
    public void setScaleFactor(double newScaleFactor) {
        scaleFactor = newScaleFactor;
    }

    /** @return the scale factor */
    public double getScaleFactor() {
        return scaleFactor;
    }

    /** @see org.apache.fop.render.Renderer#startRenderer(java.io.OutputStream) */
    public void startRenderer(OutputStream out) throws IOException {
        // do nothing by default
    }

    /** @see org.apache.fop.render.Renderer#stopRenderer() */
    public void stopRenderer() throws IOException {
        log.debug("Java2DRenderer stopped");
        renderingDone = true;
        numberOfPages = currentPageNumber;
        // TODO set all vars to null for gc
        if (numberOfPages == 0) {
            new FOPException("No page could be rendered");
        }
    }

    /** @return true if the renderer is not currently processing */
    public boolean isRenderingDone() {
        return this.renderingDone;
    }
    
    /**
     * @return The 0-based current page number
     */
    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    /**
     * @param c the 0-based current page number
     */
    public void setCurrentPageNumber(int c) {
        this.currentPageNumber = c;
    }

    /**
     * @return The 0-based total number of rendered pages
     */
    public int getNumberOfPages() {
            return numberOfPages;
    }

    /**
     * Clears the ViewportList.
     * Used if the document is reloaded.
     */
    public void clearViewportList() {
        pageViewportList.clear();
        setCurrentPageNumber(0);
    }

    /**
     * This method override only stores the PageViewport in a List. No actual
     * rendering is performed here. A renderer override renderPage() to get the
     * freshly produced PageViewport, and rendere them on the fly (producing the
     * desired BufferedImages by calling getPageImage(), which lazily starts the
     * rendering process).
     *
     * @param pageViewport the <code>PageViewport</code> object supplied by
     * the Area Tree
     * @throws IOException In case of an I/O error
     * @see org.apache.fop.render.Renderer
     */
    public void renderPage(PageViewport pageViewport) throws IOException {
        // TODO clone?
        pageViewportList.add(pageViewport.clone());
        currentPageNumber++;
    }

    /**
     * Generates a desired page from the renderer's page viewport list.
     *
     * @param pageViewport the PageViewport to be rendered
     * @return the <code>java.awt.image.BufferedImage</code> corresponding to
     * the page or null if the page doesn't exist.
     */
    public BufferedImage getPageImage(PageViewport pageViewport) {

        this.currentPageViewport = pageViewport;
        try {
            Rectangle2D bounds = pageViewport.getViewArea();
            pageWidth = (int) Math.round(bounds.getWidth() / 1000f);
            pageHeight = (int) Math.round(bounds.getHeight() / 1000f);

            log.info(
                    "Rendering Page " + pageViewport.getPageNumberString()
                            + " (pageWidth " + pageWidth + ", pageHeight "
                            + pageHeight + ")");

            double scaleX = scaleFactor 
                * (25.4 / FOUserAgent.DEFAULT_TARGET_RESOLUTION) 
                / userAgent.getTargetPixelUnitToMillimeter();
            double scaleY = scaleFactor
                * (25.4 / FOUserAgent.DEFAULT_TARGET_RESOLUTION)
                / userAgent.getTargetPixelUnitToMillimeter();
            int bitmapWidth = (int) ((pageWidth * scaleX) + 0.5);
            int bitmapHeight = (int) ((pageHeight * scaleY) + 0.5);
                    
            
            BufferedImage currentPageImage = new BufferedImage(
                    bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB);
            // FIXME TYPE_BYTE_BINARY ?

            Graphics2D graphics = currentPageImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            if (antialiasing) {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            if (qualityRendering) {
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
            }

            // transform page based on scale factor supplied
            AffineTransform at = graphics.getTransform();
            at.scale(scaleX, scaleY);
            graphics.setTransform(at);

            // draw page frame
            graphics.setColor(Color.white);
            graphics.fillRect(0, 0, pageWidth, pageHeight);
            graphics.setColor(Color.black);
            graphics.drawRect(-1, -1, pageWidth + 2, pageHeight + 2);
            graphics.drawLine(pageWidth + 2, 0, pageWidth + 2, pageHeight + 2);
            graphics.drawLine(pageWidth + 3, 1, pageWidth + 3, pageHeight + 3);
            graphics.drawLine(0, pageHeight + 2, pageWidth + 2, pageHeight + 2);
            graphics.drawLine(1, pageHeight + 3, pageWidth + 3, pageHeight + 3);

            state = new Java2DGraphicsState(graphics, this.fontInfo, at);

            // reset the current Positions
            currentBPPosition = 0;
            currentIPPosition = 0;

            // this toggles the rendering of all areas
            renderPageAreas(pageViewport.getPage());
            return currentPageImage;
        } finally {
            this.currentPageViewport = null;
        }
    }

        
    /**
     * Returns a page viewport.
     * @param pageNum the page number
     * @return the requested PageViewport instance
     * @exception FOPException If the page is out of range.
     */
    public PageViewport getPageViewport(int pageNum) throws FOPException {
        if (pageNum < 0 || pageNum >= pageViewportList.size()) {
            throw new FOPException("Requested page number is out of range: " + pageNum
                     + "; only " + pageViewportList.size()
                     + " page(s) available.");
        }
        return (PageViewport) pageViewportList.get(pageNum);
    }

    /**
     * Generates a desired page from the renderer's page viewport list.
     *
     * @param pageNum the 0-based page number to generate
     * @return the <code>java.awt.image.BufferedImage</code> corresponding to
     * the page or null if the page doesn't exist.
     * @throws FOPException If there's a problem preparing the page image
     */
    public BufferedImage getPageImage(int pageNum) throws FOPException {
        return getPageImage(getPageViewport(pageNum));
    }

    /** Saves the graphics state of the rendering engine. */
    protected void saveGraphicsState() {
        // push (and save) the current graphics state
        state.push();
    }

    /** Restores the last graphics state of the rendering engine. */
    protected void restoreGraphicsState() {
        state.pop();
    }
    
    /**
     * @see org.apache.fop.render.AbstractRenderer#startVParea(CTM, Rectangle2D)
     */
    protected void startVParea(CTM ctm, Rectangle2D clippingRect) {

        saveGraphicsState();

        if (clippingRect != null) {
            clipRect((float)clippingRect.getX() / 1000f, 
                    (float)clippingRect.getY() / 1000f, 
                    (float)clippingRect.getWidth() / 1000f, 
                    (float)clippingRect.getHeight() / 1000f);
        }

        // Set the given CTM in the graphics state
        //state.setTransform(new AffineTransform(CTMHelper.toPDFArray(ctm)));
        state.transform(new AffineTransform(CTMHelper.toPDFArray(ctm)));
    }

    /**
     * @see org.apache.fop.render.AbstractRenderer#endVParea()
     */
    protected void endVParea() {
        restoreGraphicsState();
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#restoreStateStackAfterBreakOut(
     *          java.util.List)
     */
    protected void restoreStateStackAfterBreakOut(List breakOutList) {
        log.debug(
                "Block.FIXED --> restoring context after break-out");
        Graphics2D graph;
        Iterator i = breakOutList.iterator();
        while (i.hasNext()) {
            graph = (Graphics2D) i.next();
            log.debug("Restoring: " + graph);
            state.push();
        }
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#breakOutOfStateStack()
     */
    protected List breakOutOfStateStack() {
        List breakOutList;
        log.debug("Block.FIXED --> break out");
        breakOutList = new java.util.ArrayList();
        Graphics2D graph;
        while (true) {
            graph = state.getGraph();
            if (state.pop() == null) {
                break;
            }
            breakOutList.add(0, graph); // Insert because of
            // stack-popping
            log.debug("Adding to break out list: " + graph);
        }
        return breakOutList;
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#updateColor(
     *          org.apache.fop.datatypes.ColorType, boolean)
     */
    protected void updateColor(Color col, boolean fill) {
        state.updateColor(col);
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#clip()
     */
    protected void clip() {
        if (currentPath == null) {
            throw new IllegalStateException("No current path available!");
        }
        state.updateClip(currentPath);
        currentPath = null;
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#closePath()
     */
    protected void closePath() {
        currentPath.closePath();
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#lineTo(float, float)
     */
    protected void lineTo(float x, float y) {
        if (currentPath == null) {
            currentPath = new GeneralPath();
        }
        currentPath.lineTo(x, y);
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#moveTo(float, float)
     */
    protected void moveTo(float x, float y) {
        if (currentPath == null) {
            currentPath = new GeneralPath();
        }
        currentPath.moveTo(x, y);
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#clipRect(float, float, float, float)
     */
    protected void clipRect(float x, float y, float width, float height) {
        state.updateClip(new Rectangle2D.Float(x, y, width, height));
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#fillRect(float, float, float, float)
     */
    protected void fillRect(float x, float y, float width, float height) {
        state.getGraph().fill(new Rectangle2D.Float(x, y, width, height));
    }
    
    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#drawBorderLine(
     *          float, float, float, float, boolean, boolean, int, 
     *          org.apache.fop.datatypes.ColorType)
     */
    protected void drawBorderLine(float x1, float y1, float x2, float y2, 
            boolean horz, boolean startOrBefore, int style, Color col) {
        Graphics2D g2d = state.getGraph();
        drawBorderLine(new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1), 
                horz, startOrBefore, style, col, g2d);
    }

    /**
     * Draw a border segment of an XSL-FO style border.
     * @param lineRect the line defined by its bounding rectangle
     * @param horz true for horizontal border segments, false for vertical border segments
     * @param startOrBefore true for border segments on the start or before edge, 
     *                      false for end or after.
     * @param style the border style (one of Constants.EN_DASHED etc.)
     * @param col the color for the border segment
     * @param g2d the Graphics2D instance to paint to
     */
    public static void drawBorderLine(Rectangle2D.Float lineRect, 
            boolean horz, boolean startOrBefore, int style, Color col, Graphics2D g2d) {
        float x1 = lineRect.x;
        float y1 = lineRect.y;
        float x2 = x1 + lineRect.width;
        float y2 = y1 + lineRect.height;
        float w = lineRect.width;
        float h = lineRect.height;
        if ((w < 0) || (h < 0)) {
            log.error("Negative extent received. Border won't be painted.");
            return;
        }
        switch (style) {
            case Constants.EN_DASHED: 
                g2d.setColor(col);
                if (horz) {
                    float unit = Math.abs(2 * h);
                    int rep = (int)(w / unit);
                    if (rep % 2 == 0) {
                        rep++;
                    }
                    unit = w / rep;
                    float ym = y1 + (h / 2);
                    BasicStroke s = new BasicStroke(h, BasicStroke.CAP_BUTT, 
                            BasicStroke.JOIN_MITER, 10.0f, new float[] {unit}, 0);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(x1, ym, x2, ym));
                } else {
                    float unit = Math.abs(2 * w);
                    int rep = (int)(h / unit);
                    if (rep % 2 == 0) {
                        rep++;
                    }
                    unit = h / rep;
                    float xm = x1 + (w / 2);
                    BasicStroke s = new BasicStroke(w, BasicStroke.CAP_BUTT, 
                            BasicStroke.JOIN_MITER, 10.0f, new float[] {unit}, 0);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(xm, y1, xm, y2));
                }
                break;
            case Constants.EN_DOTTED:
                g2d.setColor(col);
                if (horz) {
                    float unit = Math.abs(2 * h);
                    int rep = (int)(w / unit);
                    if (rep % 2 == 0) {
                        rep++;
                    }
                    unit = w / rep;
                    float ym = y1 + (h / 2);
                    BasicStroke s = new BasicStroke(h, BasicStroke.CAP_ROUND, 
                            BasicStroke.JOIN_MITER, 10.0f, new float[] {0, unit}, 0);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(x1, ym, x2, ym));
                } else {
                    float unit = Math.abs(2 * w);
                    int rep = (int)(h / unit);
                    if (rep % 2 == 0) {
                        rep++;
                    }
                    unit = h / rep;
                    float xm = x1 + (w / 2);
                    BasicStroke s = new BasicStroke(w, BasicStroke.CAP_ROUND, 
                            BasicStroke.JOIN_MITER, 10.0f, new float[] {0, unit}, 0);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(xm, y1, xm, y2));
                }
                break;
            case Constants.EN_DOUBLE:
                g2d.setColor(col);
                if (horz) {
                    float h3 = h / 3;
                    float ym1 = y1 + (h3 / 2);
                    float ym2 = ym1 + h3 + h3;
                    BasicStroke s = new BasicStroke(h3);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(x1, ym1, x2, ym1));
                    g2d.draw(new Line2D.Float(x1, ym2, x2, ym2));
                } else {
                    float w3 = w / 3;
                    float xm1 = x1 + (w3 / 2);
                    float xm2 = xm1 + w3 + w3;
                    BasicStroke s = new BasicStroke(w3);
                    g2d.setStroke(s);
                    g2d.draw(new Line2D.Float(xm1, y1, xm1, y2));
                    g2d.draw(new Line2D.Float(xm2, y1, xm2, y2));
                }
                break;
            case Constants.EN_GROOVE:
            case Constants.EN_RIDGE:
                float colFactor = (style == EN_GROOVE ? 0.4f : -0.4f);
                if (horz) {
                    Color uppercol = lightenColor(col, -colFactor);
                    Color lowercol = lightenColor(col, colFactor);
                    float h3 = h / 3;
                    float ym1 = y1 + (h3 / 2);
                    g2d.setStroke(new BasicStroke(h3));
                    g2d.setColor(uppercol);
                    g2d.draw(new Line2D.Float(x1, ym1, x2, ym1));
                    g2d.setColor(col);
                    g2d.draw(new Line2D.Float(x1, ym1 + h3, x2, ym1 + h3));
                    g2d.setColor(lowercol);
                    g2d.draw(new Line2D.Float(x1, ym1 + h3 + h3, x2, ym1 + h3 + h3));
                } else {
                    Color leftcol = lightenColor(col, -colFactor);
                    Color rightcol = lightenColor(col, colFactor);
                    float w3 = w / 3;
                    float xm1 = x1 + (w3 / 2);
                    g2d.setStroke(new BasicStroke(w3));
                    g2d.setColor(leftcol);
                    g2d.draw(new Line2D.Float(xm1, y1, xm1, y2));
                    g2d.setColor(col);
                    g2d.draw(new Line2D.Float(xm1 + w3, y1, xm1 + w3, y2));
                    g2d.setColor(rightcol);
                    g2d.draw(new Line2D.Float(xm1 + w3 + w3, y1, xm1 + w3 + w3, y2));
                }
                break;
            case Constants.EN_INSET:
            case Constants.EN_OUTSET:
                colFactor = (style == EN_OUTSET ? 0.4f : -0.4f);
                if (horz) {
                    col = lightenColor(col, (startOrBefore ? 1 : -1) * colFactor);
                    g2d.setStroke(new BasicStroke(h));
                    float ym1 = y1 + (h / 2);
                    g2d.setColor(col);
                    g2d.draw(new Line2D.Float(x1, ym1, x2, ym1));
                } else {
                    col = lightenColor(col, (startOrBefore ? 1 : -1) * colFactor);
                    float xm1 = x1 + (w / 2);
                    g2d.setStroke(new BasicStroke(w));
                    g2d.setColor(col);
                    g2d.draw(new Line2D.Float(xm1, y1, xm1, y2));
                }
                break;
            case Constants.EN_HIDDEN:
                break;
            default:
                g2d.setColor(col);
                if (horz) {
                    float ym = y1 + (h / 2);
                    g2d.setStroke(new BasicStroke(h));
                    g2d.draw(new Line2D.Float(x1, ym, x2, ym));
                } else {
                    float xm = x1 + (w / 2);
                    g2d.setStroke(new BasicStroke(w));
                    g2d.draw(new Line2D.Float(xm, y1, xm, y2));
                }
        }
    }

    /**
     * @see org.apache.fop.render.AbstractRenderer#renderText(TextArea)
     */
    public void renderText(TextArea text) {
        renderInlineAreaBackAndBorders(text);

        int rx = currentIPPosition + text.getBorderAndPaddingWidthStart();
        int bl = currentBPPosition + text.getOffset() + text.getBaselineOffset();

        Font font = getFontFromArea(text);
        state.updateFont(font.getFontName(), font.getFontSize(), null);

        Color col = (Color) text.getTrait(Trait.COLOR);
        state.updateColor(col);

        String s = text.getText();
        state.getGraph().drawString(s, rx / 1000f, bl / 1000f);

        super.renderText(text);

        // rendering text decorations
        Typeface tf = (Typeface) fontInfo.getFonts().get(font.getFontName());
        int fontsize = text.getTraitAsInteger(Trait.FONT_SIZE);
        renderTextDecoration(tf, fontsize, text, bl, rx);
    }

    /**
     * Render leader area. This renders a leader area which is an area with a
     * rule.
     *
     * @param area the leader area to render
     */
    public void renderLeader(Leader area) {
        renderInlineAreaBackAndBorders(area);

        // TODO leader-length: 25%, 50%, 75%, 100% not working yet
        // TODO Colors do not work on Leaders yet

        float startx = (currentIPPosition + area.getBorderAndPaddingWidthStart()) / 1000f;
        float starty = ((currentBPPosition + area.getOffset()) / 1000f);
        float endx = (currentIPPosition + area.getBorderAndPaddingWidthStart() 
                + area.getIPD()) / 1000f;

        Color col = (Color) area.getTrait(Trait.COLOR);
        state.updateColor(col);

        Line2D line = new Line2D.Float();
        line.setLine(startx, starty, endx, starty);
        float ruleThickness = area.getRuleThickness() / 1000f;

        int style = area.getRuleStyle();
        switch (style) {
        case EN_SOLID:
        case EN_DASHED:
        case EN_DOUBLE:
            drawBorderLine(startx, starty, endx, starty + ruleThickness, 
                    true, true, style, col);
            break;
        case EN_DOTTED:
            //TODO Dots should be shifted to the left by ruleThickness / 2
            state.updateStroke(ruleThickness, style);
            float rt2 = ruleThickness / 2f;
            line.setLine(line.getX1(), line.getY1() + rt2, line.getX2(), line.getY2() + rt2);
            state.getGraph().draw(line);
            break;
        case EN_GROOVE:
        case EN_RIDGE:
            float half = area.getRuleThickness() / 2000f;

            state.updateColor(lightenColor(col, 0.6f));
            moveTo(startx, starty);
            lineTo(endx, starty);
            lineTo(endx, starty + 2 * half);
            lineTo(startx, starty + 2 * half);
            closePath();
            state.getGraph().fill(currentPath);
            currentPath = null;
            state.updateColor(col);
            if (style == EN_GROOVE) {
                moveTo(startx, starty);
                lineTo(endx, starty);
                lineTo(endx, starty + half);
                lineTo(startx + half, starty + half);
                lineTo(startx, starty + 2 * half);
            } else {
                moveTo(endx, starty);
                lineTo(endx, starty + 2 * half);
                lineTo(startx, starty + 2 * half);
                lineTo(startx, starty + half);
                lineTo(endx - half, starty + half);
            }
            closePath();
            state.getGraph().fill(currentPath);
            currentPath = null;

        case EN_NONE:
            // No rule is drawn
            break;
        default:
        } // end switch

        super.renderLeader(area);
    }

    /**
     * @see org.apache.fop.render.AbstractRenderer#renderImage(Image,
     * Rectangle2D)
     */
    public void renderImage(Image image, Rectangle2D pos) {
        // endTextObject();
        String url = image.getURL();
        drawImage(url, pos);
    }

    /**
     * @see org.apache.fop.render.AbstractPathOrientedRenderer#drawImage(
     *          java.lang.String, java.awt.geom.Rectangle2D, java.util.Map)
     */
    protected void drawImage(String url, Rectangle2D pos, Map foreignAttributes) {

        int x = currentIPPosition + (int)Math.round(pos.getX());
        int y = currentBPPosition + (int)Math.round(pos.getY());
        url = ImageFactory.getURL(url);

        ImageFactory fact = userAgent.getFactory().getImageFactory();
        FopImage fopimage = fact.getImage(url, userAgent);

        if (fopimage == null) {
            return;
        }
        if (!fopimage.load(FopImage.DIMENSIONS)) {
            return;
        }
        int w = fopimage.getWidth();
        int h = fopimage.getHeight();
        String mime = fopimage.getMimeType();
        if ("text/xml".equals(mime)) {
            if (!fopimage.load(FopImage.ORIGINAL_DATA)) {
                return;
            }
            Document doc = ((XMLImage) fopimage).getDocument();
            String ns = ((XMLImage) fopimage).getNameSpace();
            renderDocument(doc, ns, pos, foreignAttributes);

        } else if ("image/svg+xml".equals(mime)) {
            if (!fopimage.load(FopImage.ORIGINAL_DATA)) {
                return;
            }
            Document doc = ((XMLImage) fopimage).getDocument();
            String ns = ((XMLImage) fopimage).getNameSpace();

            renderDocument(doc, ns, pos, foreignAttributes);
        } else if ("image/eps".equals(mime)) {
            log.warn("EPS images are not supported by this renderer");
        } else {
            if (!fopimage.load(FopImage.BITMAP)) {
                log.warn("Loading of bitmap failed: " + url);
                return;
            }

            byte[] raw = fopimage.getBitmaps();

            // TODO Hardcoded color and sample models, FIX ME!
            ColorModel cm = new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), 
                    new int[] {8, 8, 8},
                    false, false,
                    ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
            SampleModel sampleModel = new PixelInterleavedSampleModel(
                    DataBuffer.TYPE_BYTE, w, h, 3, w * 3, new int[] {0, 1, 2});
            DataBuffer dbuf = new DataBufferByte(raw, w * h * 3);

            WritableRaster raster = Raster.createWritableRaster(sampleModel,
                    dbuf, null);

            java.awt.Image awtImage;
            // Combine the color model and raster into a buffered image
            awtImage = new BufferedImage(cm, raster, false, null);

            state.getGraph().drawImage(awtImage, 
                    (int)(x / 1000f), (int)(y / 1000f), 
                    (int)(pos.getWidth() / 1000f), (int)(pos.getHeight() / 1000f), null);
        }
    }

    /**
     * @see org.apache.fop.render.PrintRenderer#createRendererContext(
     *          int, int, int, int, java.util.Map)
     */
    protected RendererContext createRendererContext(int x, int y, int width, int height, 
            Map foreignAttributes) {
        RendererContext context = super.createRendererContext(
                x, y, width, height, foreignAttributes);
        context.setProperty(Java2DRendererContextConstants.JAVA2D_STATE, state);
        return context;
    }

    /**
     * @see java.awt.print.Printable#print(java.awt.Graphics,
     * java.awt.print.PageFormat, int)
     */
    public int print(Graphics g, PageFormat pageFormat, int pageIndex)
            throws PrinterException {
        if (pageIndex >= getNumberOfPages()) {
            return NO_SUCH_PAGE;
        }

        Graphics2D graphics = (Graphics2D) g;
        Java2DGraphicsState oldState = state;
        try {
            PageViewport viewport = getPageViewport(pageIndex);
            AffineTransform at = graphics.getTransform();
            state = new Java2DGraphicsState(graphics, this.fontInfo, at);

            // reset the current Positions
            currentBPPosition = 0;
            currentIPPosition = 0;

            renderPageAreas(viewport.getPage());
            return PAGE_EXISTS;
        } catch (FOPException e) {
            log.error(e);
            return NO_SUCH_PAGE;
        } finally {
            state = oldState;
        }
    }

    /** @see org.apache.fop.render.AbstractPathOrientedRenderer#beginTextObject() */
    protected void beginTextObject() {
        //not necessary in Java2D
    }

    /** @see org.apache.fop.render.AbstractPathOrientedRenderer#endTextObject() */
    protected void endTextObject() {
        //not necessary in Java2D
    }

}
