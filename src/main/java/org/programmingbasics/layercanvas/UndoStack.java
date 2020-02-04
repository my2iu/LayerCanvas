package org.programmingbasics.layercanvas;

import java.util.ArrayList;
import java.util.List;

public class UndoStack
{
   List<UndoableCommand> stack = new ArrayList<>();
   int maxSize = 5;
   int idx = 0;
   
   public UndoableCommand undo()
   {
      if (idx <= 0) return null;
      idx--;
      UndoableCommand toReturn = stack.get(idx);
      return toReturn;
   }
   
   public UndoableCommand redo()
   {
      if (idx >= stack.size()) return null;
      UndoableCommand toReturn = stack.get(idx);
      idx++;
      return toReturn;
   }
   
   public void push(UndoableCommand command)
   {
      while (idx < stack.size())
         stack.remove(stack.size() - 1);
      stack.add(command);
      if (stack.size() > maxSize)
         stack.remove(0);
      idx = stack.size();
   }
}
