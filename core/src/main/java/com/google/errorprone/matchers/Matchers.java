/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.matchers;

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.MethodVisibility.Visibility;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;

import java.util.List;

import javax.lang.model.element.Modifier;

/**
 * Static factory methods which make the DSL read better.
 *
 * TODO: it's probably worth the optimization to keep a single instance of each Matcher, rather than
 * create new instances each time the static method is called.
 *
 * @author alexeagle@google.com (Alex Eagle)
 */
public class Matchers {
  private Matchers() {}

  /**
   * A matcher that always returns true.
   */
  public static <T extends Tree> Matcher<T> anything() {
    return new Matcher<T>() {
      @Override
      public boolean matches(T t, VisitorState state) {
        return true;
      }
    };
  }

  public static <T extends Tree> Matcher<T> allOf(
      Class<T> typeInfer, final Matcher<? super T>... matchers) {
    return allOf(matchers);
  }

  public static <T extends Tree> Matcher<T> allOf(final Matcher<? super T>... matchers) {
    return new Matcher<T>() {
      @Override public boolean matches(T t, VisitorState state) {
        for (Matcher<? super T> matcher : matchers) {
          if (!matcher.matches(t, state)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  public static <T extends Tree> Matcher<T> anyOf(
      Class<T> typeInfer, final Matcher<? super T>... matchers) {
    return anyOf(matchers);
  }

  public static <T extends Tree> Matcher<T> anyOf(final Matcher<? super T>... matchers) {
    return new Matcher<T>() {
      @Override public boolean matches(T t, VisitorState state) {
        for (Matcher<? super T> matcher : matchers) {
          if (matcher.matches(t, state)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  public static <T extends Tree> Matcher<T> kindIs(final Kind kind) {
    return new Matcher<T>() {
      @Override public boolean matches(T tree, VisitorState state) {
        return tree.getKind() == kind;
      }
    };
  }

  public static <T extends Tree> Matcher<T> kindIs(final Kind kind, Class<T> typeInfer) {
    return new Matcher<T>() {
      @Override public boolean matches(T tree, VisitorState state) {
        return tree.getKind() == kind;
      }
    };
  }

  public static <T extends Tree> Matcher<T> isNull() {
    return new Matcher<T>() {
      @Override public boolean matches(T tree, VisitorState state) {
        return tree == null;
      }
    };
  }

  public static <T extends Tree> Matcher<T> isNull(Class<T> typeInfer) {
    return new Matcher<T>() {
      @Override public boolean matches(T tree, VisitorState state) {
        return tree == null;
      }
    };
  }

  public static StaticMethod staticMethod(String fullClassName, String methodName) {
    return new StaticMethod(fullClassName, methodName);
  }

  public static InstanceMethod instanceMethod(Matcher<ExpressionTree> receiverMatcher,
      String methodName) {
    return new InstanceMethod(receiverMatcher, methodName);
  }

  /**
   * Determines whether the receiver of an instance method is the same reference as one of
   * the arguments to the method.
   *
   * @param argNum The number of the argument to compare against.
   */
  public static Matcher<? super MethodInvocationTree> receiverSameAsArgument(final int argNum) {
    return new Matcher<MethodInvocationTree>() {
      @Override
      public boolean matches(MethodInvocationTree t, VisitorState state) {
        List<? extends ExpressionTree> args = t.getArguments();
        if (args.size() <= argNum) {
          return false;
        }
        ExpressionTree arg = args.get(argNum);

        JCExpression methodSelect = (JCExpression) t.getMethodSelect();
        if (methodSelect instanceof JCFieldAccess) {
          JCFieldAccess fieldAccess = (JCFieldAccess) methodSelect;
          return ASTHelpers.sameVariable(fieldAccess.getExpression(), arg);
        } else if (methodSelect instanceof JCIdent) {
          // A bare method call: "equals(foo)".  Receiver is implicitly "this".
          return "this".equals(arg.toString());
        }

        return false;
      }
    };
  }

  public static Constructor constructor(String className, List<String> parameterTypes) {
    return new Constructor(className, parameterTypes);
  }

  public static Matcher<MethodInvocationTree> methodSelect(
      Matcher<ExpressionTree> methodSelectMatcher) {
    return new MethodInvocationMethodSelect(methodSelectMatcher);
  }

  public static Matcher<ExpressionTree> expressionMethodSelect(Matcher<ExpressionTree> methodSelectMatcher) {
    return new ExpressionMethodSelect(methodSelectMatcher);
  }

  public static Matcher<MethodInvocationTree> argument(
      final int position, final Matcher<ExpressionTree> argumentMatcher) {
    return new MethodInvocationArgument(position, argumentMatcher);
  }

  public static <T extends Tree> Matcher<Tree> parentNode(Matcher<T> treeMatcher) {
    return new ParentNode<T>(treeMatcher);
  }

  public static <T extends Tree> Matcher<T> isSubtypeOf(final Type type) {
    return new Matcher<T>() {
      @Override public boolean matches(Tree t, VisitorState state) {
        return state.getTypes().isSubtype(((JCTree) t).type, type);
      }
    };
  }

  public static <T extends Tree> Matcher<T> isCastableTo(final Supplier<Type> type) {
    return new Matcher<T>() {
      @Override public boolean matches(T t, VisitorState state) {
        return state.getTypes().isCastable(((JCTree)t).type, type.get(state));
      }
    };
  }

  public static <T extends Tree> Matcher<T> isSameType(final Type type) {
    return new Matcher<T>() {
      @Override public boolean matches(Tree t, VisitorState state) {
        return state.getTypes().isSameType(((JCTree) t).type, type);
      }
    };
  }

  public static <T extends Tree> Matcher<T> isArrayType() {
    return new Matcher<T>() {
      @Override public boolean matches(Tree t, VisitorState state) {
        return state.getTypes().isArray(((JCTree) t).type);
      }
    };
  }

  public static <T extends Tree> Matcher<T> isPrimitiveType() {
    return new Matcher<T>() {
      @Override public boolean matches(Tree t, VisitorState state) {
        return ((JCTree) t).type.isPrimitive();
      }
    };
  }

  public static <T extends Tree> Matcher<T> isSameType(Tree tree) {
    return isSameType(((JCTree) tree).type);
  }

  /**
   * Matches a type based on the name of the type.
   *
   * @param type A string representation of the type (e.g., "java.lang.Object")
   * @return true if the type of the tree matches the string
   */
  public static <T extends Tree> Matcher<T> isSameType(final String type) {
    return new Matcher<T>() {
      @Override public boolean matches(Tree t, VisitorState state) {
        return type.equals(((JCTree) t).type.toString());
      }
    };
  }

  public static <T extends Tree> EnclosingBlock<T> enclosingBlock(Matcher<BlockTree> matcher) {
    return new EnclosingBlock<T>(matcher);
  }

  public static <T extends Tree> EnclosingClass<T> enclosingClass(Matcher<ClassTree> matcher) {
    return new EnclosingClass<T>(matcher);
  }

  public static LastStatement lastStatement(Matcher<StatementTree> matcher) {
    return new LastStatement(matcher);
  }

  public static <T extends StatementTree> NextStatement<T> nextStatement(
      Matcher<StatementTree> matcher) {
    return new NextStatement<T>(matcher);
  }

  public static <T extends Tree> Same<T> same(T tree) {
    return new Same<T>(tree);
  }

  public static <T extends Tree> Matcher<T> not(final Matcher<T> matcher) {
    return new Matcher<T>() {
      @Override
      public boolean matches(T t, VisitorState state) {
        return !matcher.matches(t, state);
      }
    };
  }

  public static Matcher<ExpressionTree> stringLiteral(String value) {
    return new StringLiteral(value);
  }

  public static Matcher<AnnotationTree> hasElementWithValue(
      String element, Matcher<ExpressionTree> valueMatcher) {
    return new AnnotationHasElementWithValue(element, valueMatcher);
  }

  public static Matcher<AnnotationTree> isType(final String annotationClassName) {
    return new AnnotationType(annotationClassName);
  }

  public static Matcher<? super MethodInvocationTree> sameArgument(
      final int index1, final int index2) {
    return new Matcher<MethodInvocationTree>() {
      @Override
      public boolean matches(MethodInvocationTree methodInvocationTree, VisitorState state) {
        List<? extends ExpressionTree> args = methodInvocationTree.getArguments();
        return ASTHelpers.sameVariable(args.get(index1), args.get(index2));
      }
    };
  }

  /**
   * Determines whether a method has an annotation of the given type.
   *
   * @param annotationType The type of the annotation to look for (e.g, "javax.annotation.Nullable")
   */
  public static Matcher<ExpressionTree> methodHasAnnotation(final String annotationType) {
    return new Matcher<ExpressionTree>() {
      @Override
      public boolean matches (ExpressionTree methodTree, VisitorState state) {
        Symbol methodSym;
        switch (methodTree.getKind()) {
          case IDENTIFIER:
            methodSym = ((JCIdent) methodTree).sym;
            break;
          case MEMBER_SELECT:
            methodSym = ((JCFieldAccess) methodTree).sym;
            break;
          default:
            return false;
        }
        Symbol annotationSym = state.getSymbolFromString(annotationType);
        return (annotationSym != null) && (methodSym.attribute(annotationSym) != null);
      }
    };
  }

  public static Matcher<MethodTree> methodReturns(final Type returnType) {
    return new Matcher<MethodTree>() {
      @Override
      public boolean matches(MethodTree methodTree, VisitorState state) {
        Tree returnTree = methodTree.getReturnType();
        Type methodReturnType = null;
        switch (returnTree.getKind()) {
          case ARRAY_TYPE:
            methodReturnType = ((JCArrayTypeTree)returnTree).type;
            break;
          case PRIMITIVE_TYPE:
            methodReturnType = ((JCPrimitiveTypeTree)returnTree).type;
            break;
          case PARAMETERIZED_TYPE:
            methodReturnType = ((JCTypeApply)returnTree).type;
            break;
          default:
            return false;
        }
        return state.getTypes().isSameType(methodReturnType, returnType);
      }
    };
  }

  public static Matcher<MethodTree> methodIsNamed(final String methodName) {
    return new Matcher<MethodTree>() {
      @Override
      public boolean matches(MethodTree methodTree, VisitorState state) {
        return methodTree.getName().toString().equals(methodName);
      }
    };
  }

  public static Matcher<MethodTree> methodHasParameters(final Matcher<VariableTree>... variableMatcher) {
    return new Matcher<MethodTree>() {
      @Override
      public boolean matches(MethodTree methodTree, VisitorState state) {
        if (methodTree.getParameters().size() != variableMatcher.length) {
          return false;
        }
        int paramIndex = 0;
        for (Matcher<VariableTree> eachVariableMatcher : variableMatcher) {
          if (!eachVariableMatcher.matches(methodTree.getParameters().get(paramIndex++), state)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  public static Matcher<MethodTree> methodHasVisibility(final Visibility visibility) {
    return new MethodVisibility(visibility);
  }

  public static Matcher<MethodTree> methodHasModifier(final Modifier modifier) {
    return new MethodModifier(modifier);
  }

  /**
   * Returns true if some method in the class matches the given methodMatcher.
   *
   * @param methodMatcher A matcher on MethodTrees to run against all methods in this class.
   * @return True if some method in the class matches the given methodMatcher.
   */
  public static Matcher<ClassTree> hasMethod(final Matcher<MethodTree> methodMatcher) {
    return new Matcher<ClassTree>() {
      @Override
      public boolean matches(ClassTree t, VisitorState state) {
        for (Tree member : t.getMembers()) {
          if (member instanceof MethodTree) {
            if (methodMatcher.matches((MethodTree)member, state)) {
              return true;
            }
          }
        }
        return false;
      }
    };
  }

  public static Matcher<VariableTree> variableType(final Matcher<Tree> treeMatcher) {
    return new Matcher<VariableTree>() {
      @Override
      public boolean matches(VariableTree variableTree, VisitorState state) {
        return treeMatcher.matches(variableTree.getType(), state);
      }
    };
  }

  /**
   * Matches an instance method that is a descendant of the instance method specified by the
   * class name and method name.
   */
  public static Matcher<ExpressionTree> isDescendantOfMethod(String fullClassName,
      String methodName) {
    return new DescendantOf(fullClassName, methodName);
  }
}


