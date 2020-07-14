package org.programmingbasics.layercanvas;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.JavaScriptObject;

import elemental.client.Browser;
import elemental.dom.Element;
import elemental.events.Event;
import elemental.events.EventListener;
import elemental.events.MouseEvent;
import elemental.events.Touch;
import elemental.events.TouchEvent;
import elemental.events.TouchList;
import elemental.html.ArrayBuffer;
import elemental.html.CanvasElement;
import elemental.html.CanvasRenderingContext2D;
import elemental.html.DivElement;
import elemental.html.ImageData;
import elemental.html.ImageElement;
import elemental.util.SettableInt;
import jsinterop.annotations.JsMethod;
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

   /** Manages the stack of undo commands */
   UndoStack undos = new UndoStack();
   
   /** Keeps a copy of the current drawing when painting ends so that 
    * an undo can be quickly made if painting is restarted.
    */
   ImageData mainDataBackupForUndo;

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

   /** Size of brush */
   int brushSize = 5;

   /** When doing a flood fill, specifies the color to replace, and the color to fill */
   int floodFillEmptyColor = 0;
   int floodFillFillColor = 255;
   
   /** Should the image be horizontally mirrored */
   boolean mirrorMode = false;
   
   /** Info about image to stamp */
   CanvasElement imageStamp;
   
   static enum ToolMode {
      PAINT, ERASER, IMAGESTAMP, FLOODFILL
   }
   ToolMode tool = ToolMode.PAINT;
   
   public void go()
   {
      mainCtx = (CanvasRenderingContext2D)mainCanvas.getContext("2d");
      brushCtx = (CanvasRenderingContext2D)brushCanvas.getContext("2d");
      width = mainCanvas.getWidth();
      height = mainCanvas.getHeight();
      mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
      brushData = brushCtx.getImageData(0, 0, brushCanvas.getWidth(), brushCanvas.getHeight());
      mainDataBackupForUndo = backupMainForUndo();
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
      int dx = mouseX - lastMouseX;
      int dy = mouseY - lastMouseY;
      
      if (tool == ToolMode.ERASER || tool == ToolMode.PAINT)
      {
         int len = (int)Math.ceil(Math.sqrt(dx * dx + dy * dy));
         double increment = 1.0 / len;
         for (double alpha = 0; alpha <= 1; alpha += increment)
         {
            int x = (int)(mouseX * alpha + lastMouseX * (1 - alpha));
            int y = (int)(mouseY * alpha + lastMouseY * (1 - alpha));
            
            drawBrushPoint(x, y);
            if (mirrorMode)
               drawBrushPoint(width -x, y);
         }
      }
      else if (tool == ToolMode.IMAGESTAMP)
      {
         drawBrushPoint(mouseX, mouseY);
      }
      else if (tool == ToolMode.FLOODFILL)
      {
         // Flood fill only happens on mouse up in the finalizeBrushStroke step
      }
      
      lastMouseX = mouseX;
      lastMouseY = mouseY;
   }
   
   void drawBrushPoint(int px, int py)
   {
      int brushRadius = brushSize;
      if (tool == ToolMode.PAINT) 
      {
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
      else if (tool == ToolMode.ERASER)
      {
         SettableInt data = (SettableInt)mainData.getData();
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
               data.setAt(idx+3, 0);
            }
         }
      }
      else if (tool == ToolMode.IMAGESTAMP)
      {
         brushCtx.clearRect(0, 0, width, height);
         brushCtx.drawImage(imageStamp, px - imageStamp.getWidth() / 2, py - imageStamp.getHeight() / 2);
         brushData = brushCtx.getImageData(0, 0, brushCanvas.getWidth(), brushCanvas.getHeight());
      }
      else if (tool == ToolMode.FLOODFILL)
      {
         // Flood fill only happens on mouse up in the finalizeBrushStroke step
      }
   }
   
   void finalizeBrushStroke()
   {
      if (tool == ToolMode.FLOODFILL)
      {
         // Flood fill is only activated on mouse up
         doFloodFill(lastMouseX, lastMouseY);
         if (mirrorMode)
            doFloodFill(width - lastMouseX, lastMouseY);
      }
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
      createUndoFromMainData();
      mainCtx.putImageData(mainData, 0, 0);
      brushCtx.putImageData(brushData, 0, 0);
   }

   void draw()
   {
      if (tool == ToolMode.PAINT || tool == ToolMode.IMAGESTAMP)
         brushCtx.putImageData(brushData, 0, 0);
      else if (tool == ToolMode.ERASER)
         mainCtx.putImageData(mainData, 0, 0);
   }

   void createUndoFromMainData()
   {
      ImageData before = mainDataBackupForUndo;
      ImageData after = backupMainForUndo();
      UndoableCommand cmd = UndoableCommand.create(before, after);
      undos.push(cmd);
      mainDataBackupForUndo = after;
   }
   
   ImageData backupMainForUndo()
   {
      ImageData backupData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
      copyImageData(mainData, backupData);
      return backupData;
   }
   
   void copyImageData(ImageData fromData, ImageData toData)
   {
      SettableInt fromRawData = (SettableInt)fromData.getData();
      SettableInt toRawData = (SettableInt)toData.getData();
      for (int n = fromData.getData().getByteLength() - 1; n >= 0; n--)
      {
         toRawData.setAt(n, fromRawData.intAt(n));
      }
   }
   
   void doFloodFill(int mouseX, int mouseY)
   {
      // Tracks spans of pixels that need to be checked to see if they can 
      // be filled with color
      class Span
      {
         Span(int startX, int endX, int y) { this.startX = startX; this.endX = endX; this.y = y; }
         int y;
         int startX, endX;
      }
      // Spans that will have to be examined
      List<Span> workList = new ArrayList<>();
      workList.add(new Span(mouseX, mouseX, mouseY));
      
      // Repeatedly get a span, fill it in where possible, and look
      // to create more spans to be examined later. This particular 
      // algorithm isn't super-optimized, and may look at each pixel at
      // least 2-3 times
      while (!workList.isEmpty())
      {
         Span span = workList.remove(workList.size() - 1);
         if (span.y < 0 || span.y >= height)
            continue;
         
         // Check for extensions horizontally to the left
         if (floodFillNeedFill(span.startX, span.y))
         {
            if (span.startX > 0 && floodFillNeedFill(span.startX - 1, span.y))
            {
               Span newSpan = new Span(span.startX - 1, span.startX - 1, span.y);
               workList.add(newSpan);
               for (int x = span.startX - 2; x >= 0; x--)
               {
                  if (!floodFillNeedFill(x, span.y))
                     break;
                  newSpan.startX = x;
               }
            }
         }

         // Check for extensions horizontally to the right
         if (floodFillNeedFill(span.endX, span.y))
         {
            if (span.endX < width - 1 && floodFillNeedFill(span.endX + 1, span.y))
            {
               Span newSpan = new Span(span.endX + 1, span.endX + 1, span.y);
               workList.add(newSpan);
               for (int x = span.endX + 2; x < width; x++)
               {
                  if (!floodFillNeedFill(x, span.y))
                     break;
                  newSpan.endX = x;
               }
            }
         }

         // Extend span vertically, and actually fill in the span
         Span aboveSpan = null;
         Span belowSpan = null;
         for (int x = span.startX; x <= span.endX; x++)
         {
            if (floodFillDoFill(x, span.y))
            {
               // Extend span above
               if (aboveSpan == null || aboveSpan.endX != x - 1)
               {
                  aboveSpan = new Span(x, x, span.y - 1);
                  workList.add(aboveSpan);
               }
               aboveSpan.endX = x;
               // Extend span below
               if (belowSpan == null || belowSpan.endX != x - 1)
               {
                  belowSpan = new Span(x, x, span.y + 1);
                  workList.add(belowSpan);
               }
               belowSpan.endX = x;
            }
         }
         // Go left
         // Go right
      }
   }

   // Returns true if a pixel was set at the given location 
   private boolean floodFillDoFill(int x, int y)
   {
      int pos = (y * width + x) * 4 + 3;
      if (mainData.getData().intAt(pos) == floodFillEmptyColor)
      {
         SettableInt mainRawData = (SettableInt)mainData.getData();
         mainRawData.setAt(pos, floodFillFillColor);
         return true;
      }
      return false;
   }

   private boolean floodFillNeedFill(int x, int y)
   {
      int pos = (y * width + x) * 4 + 3;
      return (mainData.getData().intAt(pos) == floodFillEmptyColor);
   }

   
   @JsMethod public void setBrushSize(int size)
   {
      brushSize = size;
   }
   
   @JsMethod public void setFloodFillColor(int c)
   {
      floodFillFillColor = c;
      floodFillEmptyColor = c ^ 255;
   }
   
   @JsMethod public void paintMode()
   {
      tool = ToolMode.PAINT;
   }

   @JsMethod public void eraserMode()
   {
      tool = ToolMode.ERASER;
   }
   
   @JsMethod public void stampMode(ImageElement img, double scale, double rotation)
   {
      if (!img.isComplete())
      {
         img.setOnload((evt) -> { stampMode(img, scale, rotation); });
         return;
      }
      tool = ToolMode.IMAGESTAMP;
      int size = Math.max(img.getWidth(), img.getHeight());
      size = (int)Math.ceil(scale * size); 
      size *= 2;
      if (size < 2) size = 2;
      imageStamp = (CanvasElement)Browser.getDocument().createElement("canvas");
      imageStamp.setWidth(size);
      imageStamp.setHeight(size);
      setCanvasImageSmoothing(imageStamp, false);
      CanvasRenderingContext2D imgCtx = (CanvasRenderingContext2D)imageStamp.getContext("2d");
      imgCtx.clearRect(0, 0, size, size);
      imgCtx.save();
      imgCtx.translate(size / 2, size / 2);
      imgCtx.rotate((float)rotation);
      imgCtx.scale((float)scale, (float)scale);
      imgCtx.translate(-img.getWidth() / 2, -img.getHeight() / 2);
      imgCtx.drawImage(img, 0, 0);
      imgCtx.restore();
      // Threshold the image to be safe
      ImageData imgData = imgCtx.getImageData(0, 0, size, size);
      SettableInt rawData = (SettableInt)imgData.getData();
      for (int n = 3; n < size * size * 4; n += 4)
         rawData.setAt(n, rawData.intAt(n) < 255 ? 0 : 255);
      imgCtx.putImageData(imgData, 0, 0);
   }
   
   @JsMethod public void floodFillMode()
   {
      tool = ToolMode.FLOODFILL;
   }
   
   @JsMethod public void setMirrorMode(boolean enable)
   {
      mirrorMode = enable;
   }
   
   @JsMethod public void clear()
   {
      mainCtx.clearRect(0, 0, width, height);
      mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
      createUndoFromMainData();
   }

   @JsMethod public void clearToBlack()
   {
      mainCtx.setFillStyle("black");
      mainCtx.fillRect(0, 0, width, height);
      mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
      createUndoFromMainData();
   }

   @JsMethod public void undo()
   {
      // TODO: cancel any in-progress brush-strokes
      UndoableCommand cmd = undos.undo();
      if (cmd == null) return;
      copyImageData(cmd.before, mainData);
      mainDataBackupForUndo = cmd.before;
      mainCtx.putImageData(mainData, 0, 0);
   }
   
   @JsMethod public void redo()
   {
      // TODO: cancel any in-progress brush-strokes
      UndoableCommand cmd = undos.redo();
      if (cmd == null) return;
      copyImageData(cmd.after, mainData);
      mainDataBackupForUndo = cmd.after;
      mainCtx.putImageData(mainData, 0, 0);
   }
   
   @JsMethod public String extractPngDataUrl()
   {
      finalizeBrushStroke();
      return mainCanvas.toDataURL("image/png");
   }
   
   @JsMethod public void loadInPngDataUrl(String url)
   {
      ImageElement img = (ImageElement)Browser.getDocument().createElement("img");
      img.setOnload((e) -> {
         mainCtx.drawImage(img, 0, 0);
         mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
         createUndoFromMainData();
      });
      img.setSrc(url);
   }

   @JsMethod public void extractPngArrayBuffer(JavaScriptObject callback)
   {
      finalizeBrushStroke();
      canvasToBlob(mainCanvas, callback);
   }

   private static native void canvasToBlob(CanvasElement canvas, JavaScriptObject callback) /*-{
      canvas.toBlob(function(blob) {
         var reader = new $wnd.FileReader();
         reader.onload = function(e) {
            callback(event.target.result);
         }
         reader.readAsArrayBuffer(blob);
      }, "image/png");
   }-*/;

   @JsMethod public void loadInPngArrayBuffer(ArrayBuffer arrbuff)
   {
      arrayBufferToCanvas(arrbuff, mainCanvas, () -> {
         mainData = mainCtx.getImageData(0, 0, mainCanvas.getWidth(), mainCanvas.getHeight());
         createUndoFromMainData();
      });
   }

   private static native void arrayBufferToCanvas(ArrayBuffer arrbuff, CanvasElement canvas, Runnable r) /*-{
      var blob = new Blob([arrbuff], {type:"image/png"});
      var reader = new $wnd.FileReader();
      reader.onload = function(e) {
         var img = $doc.createElement("img");
         img.onload = function(e) {
            canvas.getContext('2d').drawImage(img, 0, 0);
            r.@java.lang.Runnable::run()();
         }
         img.src = event.target.result;
      }
      reader.readAsDataURL(blob);
   }-*/;

   private static native void setCanvasImageSmoothing(CanvasElement canvas, boolean val) /*-{
     canvas.imageSmoothingEnabled = val;
   }-*/;

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

   
   @JsMethod public static LayerCanvas createUi(DivElement div, CanvasElement canvas1, CanvasElement canvas2)
   {
      LayerCanvas lc = new LayerCanvas();
      lc.eventDiv = div;
      lc.mainCanvas = canvas1;
      lc.brushCanvas = canvas2;
      lc.go();
      return lc;
   }
}
