package org.programmingbasics.layercanvas;

import elemental.html.ImageData;

public class UndoableCommand
{
   ImageData before;
   ImageData after;
   public static UndoableCommand create(ImageData before, ImageData after)
   {
      UndoableCommand command = new UndoableCommand();
      command.before = before;
      command.after = after;
      return command;
   }
}
