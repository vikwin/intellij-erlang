/*
 * Copyright 2012-2014 Sergey Ignatov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.erlang.debugger.xdebug;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.intellij.erlang.debugger.node.ErlangProcessSnapshot;
import org.intellij.erlang.debugger.node.ErlangTraceElement;
import org.intellij.erlang.psi.ErlangCompositeElement;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.ErlangFunction;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.intellij.erlang.utils.ErlangModulesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ErlangSourcePosition {
  private static final Pattern FUN_PATTERN = Pattern.compile("^-(.*)/(\\d+)-fun-(\\d+)-$");

  private final XSourcePosition mySourcePosition;
  private final String myFunctionName;
  private final int myFunctionArity;
  private final int myFunExpressionArity;

  private ErlangSourcePosition(@NotNull XSourcePosition sourcePosition,
                               @Nullable String functionName, int functionArity, int funExpressionArity) {
    mySourcePosition = sourcePosition;
    myFunctionName = functionName;
    myFunctionArity = functionArity;
    myFunExpressionArity = funExpressionArity;
  }

  @NotNull
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @Nullable
  public String getFunctionName() {
    return myFunctionName;
  }

  public int getFunctionArity() {
    return myFunctionArity;
  }

  public int getFunExpressionArity() {
    return myFunExpressionArity;
  }

  @NotNull
  public String getErlangModuleName() {
    //TODO check this works for modules like 'weird module . name.erl'
    return mySourcePosition.getFile().getNameWithoutExtension();
  }

  public int getLine() {
    return mySourcePosition.getLine();
  }

  @NotNull
  public VirtualFile getFile() {
    return mySourcePosition.getFile();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ErlangSourcePosition that = (ErlangSourcePosition) o;

    return myFunExpressionArity == that.myFunExpressionArity &&
      myFunctionArity == that.myFunctionArity &&
      Comparing.equal(myFunctionName, that.myFunctionName) &&
      Comparing.equal(getFile(), that.getFile()) &&
      Comparing.equal(getLine(), that.getLine());
  }

  @Override
  public int hashCode() {
    int result = myFunctionName != null ? myFunctionName.hashCode() : 0;
    result = 31 * result + myFunctionArity;
    result = 31 * result + myFunExpressionArity;
    result = 31 * result + getFile().hashCode();
    result = 31 * result + getLine();
    return result;
  }

  @NotNull
  public static ErlangSourcePosition create(@NotNull XSourcePosition position) {
    return new ErlangSourcePosition(position, null, -1, -1);
  }

  @Nullable
  public static ErlangSourcePosition create(@NotNull Project project, @NotNull ErlangProcessSnapshot snapshot) {
    String module = snapshot.getBreakModule();
    int line = snapshot.getBreakLine();
    return module != null ? create(project, module, line) : null;
  }

  @Nullable
  public static ErlangSourcePosition create(@NotNull Project project, @NotNull ErlangTraceElement traceElement) {
    return create(project, traceElement.getModule(), traceElement.getFunction(), traceElement.getFunctionArgs().arity());
  }

  @Nullable
  public static ErlangSourcePosition create(@NotNull Project project, @NotNull String module, int line) {
    ErlangFile file = ErlangModulesUtil.getErlangModuleFile(project, module);
    VirtualFile moduleFile = file != null ? file.getVirtualFile() : null;
    XSourcePosition sourcePosition = XDebuggerUtil.getInstance().createPosition(moduleFile, line);
    return sourcePosition != null ? new ErlangSourcePosition(sourcePosition, null, -1, -1) : null;
  }

  @Nullable
  public static ErlangSourcePosition create(@NotNull final Project project, @NotNull final String module,
                                            @NotNull String functionOrFunExpression, int arity) {
    final String functionName;
    final int functionArity;
    final int funExpressionArity;

    Matcher matcher = FUN_PATTERN.matcher(functionOrFunExpression);
    final boolean inFunExpression = matcher.matches();
    if (inFunExpression) {
      functionName = matcher.group(1);
      functionArity = Integer.parseInt(matcher.group(2));
      funExpressionArity = Integer.parseInt(matcher.group(3));
    } else {
      functionName = functionOrFunExpression;
      functionArity = arity;
      funExpressionArity = -1;
    }

    XSourcePosition position = ApplicationManager.getApplication().runReadAction(new Computable<XSourcePosition>() {
      @Nullable
      @Override
      public XSourcePosition compute() {
        ErlangFile erlangModule = ErlangModulesUtil.getErlangModuleFile(project, module);
        if (erlangModule == null) return null;

        ErlangFunction function = erlangModule.getFunction(functionName, functionArity);
        ErlangCompositeElement clarifyingElement = inFunExpression && function != null ?
          ErlangPsiImplUtil.findFunExpression(function, funExpressionArity) : function;

        VirtualFile virtualFile = erlangModule.getVirtualFile();
        int offset = clarifyingElement != null ? clarifyingElement.getTextOffset() : 0;

        return XDebuggerUtil.getInstance().createPositionByOffset(virtualFile, offset);
      }
    });

    return position != null ? new ErlangSourcePosition(position, functionName, functionArity, funExpressionArity) : null;
  }
}
