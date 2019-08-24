package org.programmingbasics.layercanvas;

import elemental.dom.Element;
import elemental.events.Event;
import elemental.events.MouseEvent;
import elemental.events.Touch;
import elemental.events.TouchEvent;
import elemental.events.TouchList;
import elemental.html.CanvasElement;
import elemental.html.CanvasRenderingContext2D;
import elemental.html.DivElement;
import elemental.html.ImageData;
import elemental.util.SettableInt;
import jsinterop.annotations.JsType;

@JsType
public class LayerCanvas
{
   // Div that we hook events to
   DivElement eventDiv;
   
   // Various canvases that are used for drawing
   CanvasElement mainCanvas;
   CanvasElement brushCanvas;
   CanvasRenderingContext2D mainCtx;
   CanvasRenderingContext2D brushCtx;
   ImageData mainData;
   ImageData brushData;
   int width;
   int height;
   
   /** For remapping mouse coordinates to canvas coordinates */
   double mouseToCanvasRescale = 1.0;

   /** Whether the mouse button was depressed on the pattern portion of the canvas */
   boolean isTrackingMouseOnPattern = false;
   
   /** Whether a touch has been initiated on the pattern portion of the canvas, plus its id */
   boolean isTrackingTouchOnPattern = false;
   int trackingTouchId = -1;

   /** Last position of a brush stroke */
   int lastMouseX;
   int lastMouseY;
   
   public void go()
   {
      mainCtx = (CanvasRenderingContext2D)mainCanvas.getContext("2d");
      brushCtx = (CanvasRenderingContext2D)brushCanvas.getContext("2d");
      width = mainCanvas.getWidth();
      height = mainCanvas.getHeight();
      mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
      brushData = brushCtx.getImageData(0, 0, brushCanvas.getWidth(), brushCanvas.getHeight());
      hookEvents();
   }
   
   void hookEvents()
   {
     // Hook mouse events
     eventDiv.addEventListener(Event.MOUSEDOWN, (e) -> {
       MouseEvent evt = (MouseEvent)e;
       evt.preventDefault();
       evt.stopPropagation();
       int mouseX = (int)(evt.getOffsetX() * mouseToCanvasRescale);
       int mouseY = (int)(evt.getOffsetY() * mouseToCanvasRescale);
       // Otherwise, check if the pattern is being drawn
//       int row = findPatternRow(mouseX, mouseY);
//       int col = findPatternCol(mouseX, mouseY);
//       if (readOnly) return;
       if (isValidStrokeStart(mouseX, mouseY))
//       if (row >= 0 && row < data.height && col >= 0 && col < data.width)
       {
//         isMouseTurnOn = !data.rows[row].data[col]; 
//         data.rows[row].data[col] = isMouseTurnOn;
         isTrackingMouseOnPattern = true;
         lastMouseX = mouseX;
         lastMouseY = mouseY;
         handleBrushStroke(mouseX, mouseY);
         draw();
//         draw();
       }
     }, false);
     eventDiv.addEventListener(Event.MOUSEMOVE, (e) -> {
       MouseEvent evt = (MouseEvent)e;
       evt.preventDefault();
       evt.stopPropagation();
       if (!isTrackingMouseOnPattern) return;
       int mouseX = (int)(evt.getOffsetX() * mouseToCanvasRescale);
       int mouseY = (int)(evt.getOffsetY() * mouseToCanvasRescale);
       handleBrushStroke(mouseX, mouseY);
       draw();
     }, false);
     eventDiv.addEventListener(Event.MOUSEUP, (e) -> {
       MouseEvent evt = (MouseEvent)e;
       evt.preventDefault();
       evt.stopPropagation();
       isTrackingMouseOnPattern = false;
       finalizeBrushStroke();
       draw();
     }, false);
     
     // Hook touch events
     eventDiv.addEventListener(Event.TOUCHSTART, (e) -> {
       TouchEvent evt = (TouchEvent)e;
       if (evt.getChangedTouches().getLength() > 1)
         return;
       Touch touch = evt.getChangedTouches().item(0);
       int mouseX = (int)(pageXRelativeToEl(touch.getPageX(), eventDiv) * mouseToCanvasRescale);
       int mouseY = (int)(pageYRelativeToEl(touch.getPageY(), eventDiv) * mouseToCanvasRescale);
       // Otherwise, check if the pattern is being drawn
//       int row = findPatternRow(mouseX, mouseY);
//       int col = findPatternCol(mouseX, mouseY);
//       if (readOnly) return;
       if (isValidStrokeStart(mouseX, mouseY))
//       if (row >= 0 && row < data.height && col >= 0 && col < data.width)
       {
//         isMouseTurnOn = !data.rows[row].data[col]; 
//         data.rows[row].data[col] = isMouseTurnOn;
         // If there are multiple touches, just reset to follow the latest one
         isTrackingTouchOnPattern = true;
         trackingTouchId = touch.getIdentifier();
         lastMouseX = mouseX;
         lastMouseY = mouseY;
         handleBrushStroke(mouseX, mouseY);
         draw();
         evt.preventDefault();
         evt.stopPropagation();
       }
     }, false);
     eventDiv.addEventListener(Event.TOUCHMOVE, (e) -> {
       TouchEvent evt = (TouchEvent)e;
       if (!isTrackingTouchOnPattern) return;
       Touch touch = findTouch(evt.getChangedTouches(), trackingTouchId);
       if (touch == null) return;
       int mouseX = (int)(pageXRelativeToEl(touch.getPageX(), eventDiv) * mouseToCanvasRescale);
       int mouseY = (int)(pageYRelativeToEl(touch.getPageY(), eventDiv) * mouseToCanvasRescale);
       handleBrushStroke(mouseX, mouseY);
       draw();
       evt.preventDefault();
       evt.stopPropagation();
     }, false);
     eventDiv.addEventListener(Event.TOUCHEND, (e) -> {
       TouchEvent evt = (TouchEvent)e;
       if (!isTrackingTouchOnPattern) return;
       Touch touch = findTouch(evt.getChangedTouches(), trackingTouchId);
       if (touch == null) return;
       int mouseX = (int)(pageXRelativeToEl(touch.getPageX(), eventDiv) * mouseToCanvasRescale);
       int mouseY = (int)(pageYRelativeToEl(touch.getPageY(), eventDiv) * mouseToCanvasRescale);
       handleBrushStroke(mouseX, mouseY);
       isTrackingTouchOnPattern = false;
       finalizeBrushStroke();
       draw();
       evt.preventDefault();
       evt.stopPropagation();
     }, false);
     eventDiv.addEventListener(Event.TOUCHCANCEL, (e) -> {
       TouchEvent evt = (TouchEvent)e;
       evt.preventDefault();
       evt.stopPropagation();
       isTrackingTouchOnPattern = false;
       finalizeBrushStroke();
       draw();
     }, false);
//     eventDiv.addEventListener(Event.DRAGSTART, (e) -> { e.preventDefault(); }, false);
   }
   
   private boolean isValidStrokeStart(int mouseX, int mouseY)
   {
      return true;
   }
   
   private Touch findTouch(TouchList touches, int identifier)
   {
     for (int n = 0; n < touches.getLength(); n++)
     {
       if (touches.item(n).getIdentifier() == identifier)
         return touches.item(n);
     }
     return null;
   }

   void handleBrushStroke(int mouseX, int mouseY)
   {
      mainCtx.setFillStyle("black");
      
      int dx = mouseX - lastMouseX;
      int dy = mouseY - lastMouseY;
      int len = (int)Math.ceil(Math.sqrt(dx * dx + dy * dy));
      double increment = 1.0 / len;
      for (double alpha = 0; alpha <= 1; alpha += increment)
      {
         int x = (int)(mouseX * alpha + lastMouseX * (1 - alpha));
         int y = (int)(mouseY * alpha + lastMouseY * (1 - alpha));
         
         drawBrushPoint(x, y);
      }
      
      
      lastMouseX = mouseX;
      lastMouseY = mouseY;
   }
   
   void drawBrushPoint(int px, int py)
   {
      int brushRadius = 5;
      SettableInt data = (SettableInt)brushData.getData();
      for (int y = - brushRadius; y <= brushRadius; y++)
      {
         int canvasY = y + py;
         if (canvasY < 0 || canvasY >= height) continue;
         int rightX = (int)Math.sqrt(brushRadius * brushRadius - y * y);
         int leftX = px-rightX;
         rightX = px + rightX;
         if (leftX < 0) leftX = 0;
         if (leftX >= width) continue;
         if (rightX >= width) rightX = width - 1;
         if (rightX < 0) continue;
         for (int idx = (canvasY * width + leftX) * 4; idx <= (canvasY * width + rightX) * 4; idx+= 4)
         {
            data.setAt(idx, 0);
            data.setAt(idx+1, 0);
            data.setAt(idx+2, 0);
            data.setAt(idx+3, 255);
         }
      }
      
   }
   
   void finalizeBrushStroke()
   {
      SettableInt mainRawData = (SettableInt)mainData.getData();
      SettableInt brushRawData = (SettableInt)brushData.getData();
      for (int n = 0; n < width * height * 4; n += 4) {
         if (brushRawData.intAt(n + 3) > 0)
         {
            mainRawData.setAt(n, brushRawData.intAt(n));
            mainRawData.setAt(n+1, brushRawData.intAt(n+1));
            mainRawData.setAt(n+2, brushRawData.intAt(n+2));
            mainRawData.setAt(n+3, brushRawData.intAt(n+3));
         }
         brushRawData.setAt(n, 0);
         brushRawData.setAt(n+1, 0);
         brushRawData.setAt(n+2, 0);
         brushRawData.setAt(n+3, 0);
      }
      mainCtx.putImageData(mainData, 0, 0);
      brushCtx.putImageData(brushData, 0, 0);
   }

   void draw()
   {
      brushCtx.putImageData(brushData, 0, 0);
   }
   
   public static int pageXRelativeToEl(int x, Element element)
   {
     // Convert pageX and pageY numbers to be relative to a certain element
     int pageX = 0, pageY = 0;
     while(element.getOffsetParent() != null)
     {
       pageX += element.getOffsetLeft();
       pageY += element.getOffsetTop();
       pageX -= element.getScrollLeft();
       pageY -= element.getScrollTop();
       element = element.getOffsetParent();
     }
     x = x - pageX;
     return x;
   }

   public static int pageYRelativeToEl(int y, Element element)
   {
     // Convert pageX and pageY numbers to be relative to a certain element
     int pageX = 0, pageY = 0;
     while(element.getOffsetParent() != null)
     {
       pageX += element.getOffsetLeft();
       pageY += element.getOffsetTop();
       pageX -= element.getScrollLeft();
       pageY -= element.getScrollTop();
       element = element.getOffsetParent();
     }
     y = y - pageY;
     return y;
   }

   
   public static LayerCanvas createUi(DivElement div, CanvasElement canvas1, CanvasElement canvas2)
   {
      LayerCanvas lc = new LayerCanvas();
      lc.eventDiv = div;
      lc.mainCanvas = canvas1;
      lc.brushCanvas = canvas2;
      lc.go();
      return lc;
   }
}
