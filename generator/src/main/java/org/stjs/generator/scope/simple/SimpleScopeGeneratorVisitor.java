/**
 *  Copyright 2011 Alexandru Craciun, Eyal Kaspi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.stjs.generator.scope.simple;

import static japa.parser.ast.body.ModifierSet.isStatic;
import static org.stjs.generator.scope.classloader.ClassWrapper.wrap;
import japa.parser.ast.BlockComment;
import japa.parser.ast.Comment;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.LineComment;
import japa.parser.ast.Node;
import japa.parser.ast.PackageDeclaration;
import japa.parser.ast.TypeParameter;
import japa.parser.ast.body.AnnotationDeclaration;
import japa.parser.ast.body.AnnotationMemberDeclaration;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.EmptyMemberDeclaration;
import japa.parser.ast.body.EmptyTypeDeclaration;
import japa.parser.ast.body.EnumConstantDeclaration;
import japa.parser.ast.body.EnumDeclaration;
import japa.parser.ast.body.FieldDeclaration;
import japa.parser.ast.body.InitializerDeclaration;
import japa.parser.ast.body.JavadocComment;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.body.Parameter;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.body.VariableDeclaratorId;
import japa.parser.ast.expr.ArrayAccessExpr;
import japa.parser.ast.expr.ArrayCreationExpr;
import japa.parser.ast.expr.ArrayInitializerExpr;
import japa.parser.ast.expr.AssignExpr;
import japa.parser.ast.expr.BinaryExpr;
import japa.parser.ast.expr.BooleanLiteralExpr;
import japa.parser.ast.expr.CastExpr;
import japa.parser.ast.expr.CharLiteralExpr;
import japa.parser.ast.expr.ClassExpr;
import japa.parser.ast.expr.ConditionalExpr;
import japa.parser.ast.expr.DoubleLiteralExpr;
import japa.parser.ast.expr.EnclosedExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.FieldAccessExpr;
import japa.parser.ast.expr.InstanceOfExpr;
import japa.parser.ast.expr.IntegerLiteralExpr;
import japa.parser.ast.expr.IntegerLiteralMinValueExpr;
import japa.parser.ast.expr.LongLiteralExpr;
import japa.parser.ast.expr.LongLiteralMinValueExpr;
import japa.parser.ast.expr.MarkerAnnotationExpr;
import japa.parser.ast.expr.MemberValuePair;
import japa.parser.ast.expr.MethodCallExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.NormalAnnotationExpr;
import japa.parser.ast.expr.NullLiteralExpr;
import japa.parser.ast.expr.ObjectCreationExpr;
import japa.parser.ast.expr.QualifiedNameExpr;
import japa.parser.ast.expr.SingleMemberAnnotationExpr;
import japa.parser.ast.expr.StringLiteralExpr;
import japa.parser.ast.expr.SuperExpr;
import japa.parser.ast.expr.ThisExpr;
import japa.parser.ast.expr.UnaryExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.AssertStmt;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.BreakStmt;
import japa.parser.ast.stmt.CatchClause;
import japa.parser.ast.stmt.ContinueStmt;
import japa.parser.ast.stmt.DoStmt;
import japa.parser.ast.stmt.EmptyStmt;
import japa.parser.ast.stmt.ExplicitConstructorInvocationStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.ForStmt;
import japa.parser.ast.stmt.ForeachStmt;
import japa.parser.ast.stmt.IfStmt;
import japa.parser.ast.stmt.LabeledStmt;
import japa.parser.ast.stmt.ReturnStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.stmt.SwitchEntryStmt;
import japa.parser.ast.stmt.SwitchStmt;
import japa.parser.ast.stmt.SynchronizedStmt;
import japa.parser.ast.stmt.ThrowStmt;
import japa.parser.ast.stmt.TryStmt;
import japa.parser.ast.stmt.TypeDeclarationStmt;
import japa.parser.ast.stmt.WhileStmt;
import japa.parser.ast.type.ClassOrInterfaceType;
import japa.parser.ast.type.PrimitiveType;
import japa.parser.ast.type.ReferenceType;
import japa.parser.ast.type.VoidType;
import japa.parser.ast.type.WildcardType;
import japa.parser.ast.visitor.VoidVisitor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.stjs.generator.ASTNodeData;
import org.stjs.generator.GeneratorConstants;
import org.stjs.generator.JavascriptGenerationException;
import org.stjs.generator.SourcePosition;
import org.stjs.generator.handlers.JavascriptWriter;
import org.stjs.generator.handlers.SpecialMethodHandlers;
import org.stjs.generator.scope.JavaTypeName;
import org.stjs.generator.scope.TypeScope;
import org.stjs.generator.scope.classloader.ClassWrapper;
import org.stjs.generator.utils.ClassUtils;
import org.stjs.generator.utils.PreConditions;
import org.stjs.javascript.annotation.GlobalScope;

/**
 * This class visits the AST corresponding to a Java file and generates the corresponding Javascript code. It presumes
 * the {@link SimpleScopeBuilder} previously visited the tree and set the resolved name of certain nodes.
 * 
 */
public class SimpleScopeGeneratorVisitor implements VoidVisitor<NameScopeWalker> {
	private final SpecialMethodHandlers specialMethodHandlers;

	JavascriptWriter printer = new JavascriptWriter();

	private List<Comment> comments;

	private int currentComment = 0;

	public SimpleScopeGeneratorVisitor() {
		specialMethodHandlers = new SpecialMethodHandlers();
	}

	public String getGeneratedSource() {
		return printer.getSource();
	}

	@Override
	public void visit(CompilationUnit n, NameScopeWalker scopeWalker) {
		comments = n.getComments();
		if (n.getTypes() != null) {
			for (Iterator<TypeDeclaration> i = n.getTypes().iterator(); i.hasNext();) {
				i.next().accept(this, scopeWalker);
				printer.printLn();
				if (i.hasNext()) {
					printer.printLn();
				}
			}
		}
	}

	@Override
	public void visit(ClassOrInterfaceType n, NameScopeWalker scopeWalker) {
		ClassScope scope = (ClassScope) scopeWalker.getScope();
		printer.print(stJsName(scope.getClazz()));
	}

	private String stJsName(ClassWrapper classWrapper) {
		// We may want to use a more complex naming scheme, to avoid conflicts across packages
		return classWrapper.getSimpleName();
	}

	@Override
	public void visit(ReferenceType n, NameScopeWalker scopeWalker) {
		// skip
	}

	@Override
	public void visit(ImportDeclaration n, NameScopeWalker scopeWalker) {
		// skip
	}

	@Override
	public void visit(PackageDeclaration n, NameScopeWalker scopeWalker) {
		// skip
	}

	@Override
	public void visit(MarkerAnnotationExpr n, NameScopeWalker scopeWalker) {
		// skip
	}

	@Override
	public void visit(SynchronizedStmt n, NameScopeWalker scopeWalker) {
		throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
				"synchronized blocks are not supported by Javascript");
	}

	@Override
	public void visit(CastExpr n, NameScopeWalker scopeWalker) {
		// skip to cast type - continue with the expression
		if (n.getExpr() != null) {
			n.getExpr().accept(this, scopeWalker);
		}
	}

	@Override
	public void visit(IntegerLiteralExpr n, NameScopeWalker context) {
		printer.printNumberLiteral(n.getValue());
	}

	@Override
	public void visit(LongLiteralExpr n, NameScopeWalker context) {
		printer.printNumberLiteral(n.getValue());
	}

	@Override
	public void visit(StringLiteralExpr n, NameScopeWalker scopeWalker) {
		printer.printStringLiteral(n.getValue());
	}

	@Override
	public void visit(CharLiteralExpr n, NameScopeWalker scopeWalker) {
		printer.printCharLiteral(n.getValue());
	}

	@Override
	public void visit(DoubleLiteralExpr n, NameScopeWalker scopeWalker) {
		printer.printNumberLiteral(n.getValue());
	}

	@Override
	public void visit(BooleanLiteralExpr n, NameScopeWalker scopeWalker) {
		printer.printLiteral(Boolean.toString(n.getValue()));
	}

	public void print(StringLiteralExpr n) {
		// java has some more syntax to declare integers :
		// 0x0, 0b0, (java7) 1_000_000
		// TxODO : convert it to plain numbers for javascript
		printer.printLiteral(n.getValue());
	}

	@Override
	public void visit(EnumDeclaration n, NameScopeWalker scopeWalker) {
		printComments(n, scopeWalker);
		// printer.print(n.getName());
		Scope scope = scopeWalker.getScope();
		printer.print(stJsName(scope.resolveType(n.getName())));
		// TxODO implements not considered
		printer.print(" = ");
		printer.printLn(" stjs.enumeration(");
		printer.indent();
		if (n.getEntries() != null) {
			for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext();) {
				EnumConstantDeclaration e = i.next();
				printer.printStringLiteral(e.getName());
				if (i.hasNext()) {
					printer.printLn(", ");
				}
			}
		}
		// TxODO members not considered
		printer.printLn("");
		printer.unindent();
		printer.print(");");
	}

	@Override
	public void visit(ForeachStmt n, NameScopeWalker scopeWalker) {
		printer.print("for (");
		n.getVariable().accept(this, scopeWalker);
		printer.print(" in ");
		n.getIterable().accept(this, scopeWalker);
		printer.print(") ");
		n.getBody().accept(this, scopeWalker);
	}

	@Override
	public void visit(IfStmt n, NameScopeWalker scopeWalker) {
		printer.print("if (");
		n.getCondition().accept(this, scopeWalker);
		printer.print(") ");
		n.getThenStmt().accept(this, scopeWalker);
		if (n.getElseStmt() != null) {
			printer.print(" else ");
			n.getElseStmt().accept(this, scopeWalker);
		}
	}

	@Override
	public void visit(WhileStmt n, NameScopeWalker scopeWalker) {
		printer.print("while (");
		n.getCondition().accept(this, scopeWalker);
		printer.print(") ");
		n.getBody().accept(this, scopeWalker);
	}

	@Override
	public void visit(ContinueStmt n, NameScopeWalker scopeWalker) {
		printer.print("continue");
		if (n.getId() != null) {
			printer.print(" ");
			printer.print(n.getId());
		}
		printer.print(";");
	}

	@Override
	public void visit(DoStmt n, NameScopeWalker scopeWalker) {
		printer.print("do ");
		n.getBody().accept(this, scopeWalker);
		printer.print(" while (");
		n.getCondition().accept(this, scopeWalker);
		printer.print(");");
	}

	@Override
	public void visit(ForStmt n, NameScopeWalker scopeWalker) {
		printer.print("for (");
		if (n.getInit() != null) {
			for (Iterator<Expression> i = n.getInit().iterator(); i.hasNext();) {
				Expression e = i.next();
				e.accept(this, scopeWalker);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print("; ");
		if (n.getCompare() != null) {
			n.getCompare().accept(this, scopeWalker);
		}
		printer.print("; ");
		if (n.getUpdate() != null) {
			for (Iterator<Expression> i = n.getUpdate().iterator(); i.hasNext();) {
				Expression e = i.next();
				e.accept(this, scopeWalker);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}
		}
		printer.print(") ");
		n.getBody().accept(this, scopeWalker);
	}

	@Override
	public void visit(VariableDeclaratorId n, NameScopeWalker scopeWalker) {
		printer.print(n.getName());
	}

	@Override
	public void visit(VariableDeclarator n, NameScopeWalker scopeWalker) {
		throw new IllegalStateException("Unexpected visit in a VariableDeclarator node:" + n);
	}

	private void printVariableDeclarator(VariableDeclarator n, NameScopeWalker scopeWalker, boolean forceInitNull) {
		n.getId().accept(this, scopeWalker);
		if (n.getInit() != null) {
			printer.print(" = ");
			n.getInit().accept(this, scopeWalker);
		} else if (forceInitNull) {
			printer.print(" = null");
		}

	}

	@Override
	public void visit(VariableDeclarationExpr n, NameScopeWalker scopeWalker) {
		// skip type
		printer.print("var ");

		for (Iterator<VariableDeclarator> i = n.getVars().iterator(); i.hasNext();) {
			VariableDeclarator v = i.next();
			printVariableDeclarator(v, scopeWalker, false);
			if (i.hasNext()) {
				printer.print(", ");
			}
		}
	}

	@Override
	public void visit(FieldDeclaration n, NameScopeWalker scopeWalker) {
		PreConditions.checkNotNull(scopeWalker.getScope());
		
		ClassScope scope = (ClassScope) scopeWalker.getScope();
		// FIXME : Enable checks
		//Checks.checkFieldDeclaration(n, scopeWalker);
		// skip type
		for (VariableDeclarator v : n.getVariables()) {
			printStaticMembersPrefix(scope);
			if (!isStatic(n.getModifiers())) {
				printer.print(".prototype");
			}
			printer.print(".");

			printVariableDeclarator(v, scopeWalker, true);
			printer.print(";");
		}
	}

	private void printJavadoc(JavadocComment javadoc, NameScopeWalker scopeWalker) {
		if (javadoc != null) {
			javadoc.accept(this, scopeWalker);
		}
	}

	private void printComments(Node n, NameScopeWalker scopeWalker) {
		if (comments == null) {
			return;
		}
		// the problem is that the comments are all attached to the root node
		// so this method will display all the comments before the given node.
		while (currentComment < comments.size()) {
			if (comments.get(currentComment).getBeginLine() < n.getBeginLine()) {
				comments.get(currentComment).accept(this, scopeWalker);
			} else {
				break;
			}
			currentComment++;
		}
	}

	private void printMethod(String name, List<Parameter> parameters, int modifiers, BlockStmt body,
			NameScopeWalker scopeWalker, boolean anonymous) {

		if (ModifierSet.isAbstract(modifiers) || ModifierSet.isNative(modifiers)) {
			return;
		}

		if (anonymous) {
			printer.print("function");
		} else {
			printStaticMembersPrefix((ClassScope) scopeWalker.getScope());
			if (!isStatic(modifiers)) {
				printer.print(".prototype");
			}
			printer.print(".");
			printer.print(name);
			printer.print(" = function");
		}

		printer.print("(");
		if (parameters != null) {
			boolean first = true;
			for (Parameter p : parameters) {
				// don't display the special THIS parameter
				if (GeneratorConstants.SPECIAL_THIS.equals(p.getId().getName())) {
					continue;
				}
				if (!first) {
					printer.print(", ");
				}
				p.accept(this, scopeWalker);
				first = false;
			}
		}
		printer.print(")");
		// skip throws
		if (body == null) {
			printer.print("{}");
		} else {
			printer.print(" ");
			body.accept(this, scopeWalker);
		}
		if (!anonymous) {
			printer.print(";");
		}
	}

	private MethodDeclaration getMethodDeclaration(ObjectCreationExpr n) {
		MethodDeclaration singleMethod = null;
		for (BodyDeclaration d : n.getAnonymousClassBody()) {
			if (d instanceof MethodDeclaration) {
				if (singleMethod != null) {
					// there are more methods -> back to standard declaration
					return null;
				}
				singleMethod = (MethodDeclaration) d;
			} else if (d instanceof FieldDeclaration) {
				// back to standard declaration
				return null;
			}
		}
		return singleMethod;
	}

	void printscopeWalkeruments(List<Expression> scopeWalkers, NameScopeWalker scopeWalker) {
		printscopeWalkeruments(Collections.<String> emptyList(), scopeWalkers, Collections.<String> emptyList(), scopeWalker);
	}

	void printscopeWalkeruments(Collection<String> beforeParams, Collection<Expression> scopeWalkers, Collection<String> afterParams,
			NameScopeWalker scopeWalker) {
		printer.print("(");
		boolean first = true;
		for (String param : beforeParams) {
			if (!first) {
				printer.print(", ");
			}
			printer.print(param);
			first = false;
		}
		if (scopeWalkers != null) {
			for (Expression e : scopeWalkers) {
				if (!first) {
					printer.print(", ");
				}
				e.accept(this, scopeWalker);
				first = false;
			}
		}
		for (String param : afterParams) {
			if (!first) {
				printer.print(", ");
			}
			printer.print(param);
			first = false;
		}
		printer.print(")");
	}

	private InitializerDeclaration getInitializerDeclaration(ObjectCreationExpr n) {
		if (n.getAnonymousClassBody() == null) {
			return null;
		}
		for (BodyDeclaration d : n.getAnonymousClassBody()) {
			if (d instanceof InitializerDeclaration) {
				return (InitializerDeclaration) d;
			}
		}
		return null;
	}

	private ClassOrInterfaceDeclaration buildClassDeclaration(String className, String extendsFrom,
			List<BodyDeclaration> members, List<Expression> constructorscopeWalkers) {
		ClassOrInterfaceDeclaration decl = new ClassOrInterfaceDeclaration();
		decl.setName(className);
		decl.setExtends(Collections.singletonList(new ClassOrInterfaceType(extendsFrom)));
		decl.setMembers(members);
		// TODO add constructor if needed to call the super with the constructorscopeWalkers
		return decl;
	}

	@Override
	public void visit(ObjectCreationExpr n, NameScopeWalker scopeWalker) {
		InitializerDeclaration block = getInitializerDeclaration(n);
		if (block != null) {
			// special construction for object initialization new Object(){{x = 1; y = 2; }};
			block.getBlock().accept(this, scopeWalker);
			return;
		}

		if ((n.getAnonymousClassBody() != null) && (n.getAnonymousClassBody().size() >= 1)) {
			// special construction for inline function definition
			MethodDeclaration method = getMethodDeclaration(n);
			if (method != null) {
				printMethod(method.getName(), method.getParameters(), method.getModifiers(), method.getBody(), scopeWalker,
						true);
				return;
			}
			// special construction to handle the inline body
			// build a special type called _InlineType to handle this
			/* FIXME : fix inline types
			printer.printLn("(function(){");
			ClassOrInterfaceDeclaration inlineFakeClass = buildClassDeclaration(GeneratorConstants.SPECIAL_INLINE_TYPE,
					n.getType().getName(), n.getAnonymousClassBody(), n.getArgs());
			inlineFakeClass.setData(n.getData());
			inlineFakeClass.accept(this, scopeWalker);

			printer.printLn("");
			printer.print("return new ").print(GeneratorConstants.SPECIAL_INLINE_TYPE);
			printscopeWalkeruments(n.getArgs(), scopeWalker);
			printer.printLn(";");
			printer.print("})()");*/
			return;
			
		}

		ClassWrapper clazz = scopeWalker.getScope().resolveType(n.getType().getName());
		if ((clazz != null) && ClassUtils.isDataType(clazz)) {
			// this is a call to an mock type
			printer.print("{}");
			return;
		}
		printer.print("new ");
		n.getType().accept(this, scopeWalker);
		printscopeWalkeruments(n.getArgs(), scopeWalker);
	}

	@Override
	public void visit(Parameter n, NameScopeWalker scopeWalker) {
		// skip type
		n.getId().accept(this, scopeWalker);
	}

	@Override
	public void visit(MethodDeclaration n, NameScopeWalker scopeWalker) {
		/* FIXME : Fix checks
		 * Checks.checkMethodDeclaration(n, scopeWalker);*/
		printComments(n, scopeWalker);
		printMethod(n.getName(), n.getParameters(), n.getModifiers(), n.getBody(), scopeWalker, false);
	}

	private void addCallToSuper(NameScopeWalker scopeWalker) {
		ClassScope classScope = (ClassScope) scopeWalker.getScope();
		PreConditions.checkNotNull(classScope);
		if (classScope.getClazz().getSuperclass().isDefined()) {
			// avoid useless call to super() when the super class is Object
			printer.print("this._super");
			printscopeWalkeruments(Collections.singleton("null"), Collections.<Expression> emptyList(),
					Collections.<String> emptyList(), scopeWalker);
			printer.print(";");
		}
	}

	private <T extends Node> T addParent(T node, Node parent) {
		node.setData(new ASTNodeData(parent));
		return node;
	}

	@Override
	public void visit(ConstructorDeclaration n, NameScopeWalker scopeWalker) {
		printComments(n, scopeWalker);
		if ((n.getBlock().getStmts() != null) && (n.getBlock().getStmts().size() > 0)) {
			Statement firstStatement = n.getBlock().getStmts().get(0);
			if (!(firstStatement instanceof ExplicitConstructorInvocationStmt)) {
				// generate possibly missing super() call
				n.getBlock().getStmts().add(0, addParent(new ExplicitConstructorInvocationStmt(), n.getBlock()));
				// addCallToSuper(scopeWalker);
			}
		}
		printMethod(n.getName(), n.getParameters(), n.getModifiers(), n.getBlock(), scopeWalker, true);
	}

	@Override
	public void visit(TypeParameter n, NameScopeWalker scopeWalker) {
		// skip

	}

	@Override
	public void visit(LineComment n, NameScopeWalker scopeWalker) {
		printer.print("//");
		if (n.getContent().endsWith("\n")) {
			// remove trailing enter and printLn
			// to keep indentation
			printer.printLn(n.getContent().substring(0, n.getContent().length() - 1));
		}

	}

	@Override
	public void visit(BlockComment n, NameScopeWalker scopeWalker) {
		printer.print("/*");
		printer.print(n.getContent());
		printer.printLn("*/");
	}

	@Override
	public void visit(ClassOrInterfaceDeclaration n, NameScopeWalker scopeWalker) {
		printComments(n, scopeWalker);
		// printer.print(n.getName() + " = ");
		if (GeneratorConstants.SPECIAL_INLINE_TYPE.equals(n.getName())) {
			printer.print("var ");
		} else {
			ClassWrapper type = scopeWalker.getScope().resolveType(n.getName());
			if (!type.isInnerType()) {
				printer.print("var ");
			}
		}

		Scope scope = scopeWalker.getScope();
		String className = stJsName(scope.resolveType(n.getName()));
		printer.print(className);

		NameScopeWalker classScopeWalker = scopeWalker.nextChild();
		ClassScope classScope = (ClassScope) (classScopeWalker.getScope());
		
		printer.print(" = ");
		if (n.getMembers() != null) {
			ClassOrInterfaceDeclaration prevType = scopeWalker.getContext().setCurrentType(n);
			ConstructorDeclaration constr = getConstructor(n.getMembers(), scopeWalker);
			if (constr != null) {
				constr.accept(this, scopeWalker);
				printer.print(";");
			} else {
				printer.print("function(){");
				addCallToSuper(classScopeWalker);
				printer.printLn("};");
			}

			if ((n.getExtends() != null) && (n.getExtends().size() > 0)) {
				printer.printLn();
				printer.print("stjs.extend(");

				printer.print(className);

				// TODO extends should also be with full qualifier
				printer.printLn(", " + n.getExtends().get(0).getName() + ");");
			}
			printMembers(n.getMembers(), classScopeWalker);

			printMainMethodCall(n, classScope);
			scopeWalker.getContext().setCurrentType(prevType);
		}
		
	}

	private void printMembers(List<BodyDeclaration> members, NameScopeWalker context) {
		for (BodyDeclaration member : members) {
			if (member instanceof ConstructorDeclaration) {
				context.nextChild();
				continue;
			}
			printer.printLn();
			member.accept(this, context);
		}
	}

	private ConstructorDeclaration getConstructor(List<BodyDeclaration> members, NameScopeWalker scopeWalker) {
		ConstructorDeclaration constr = null;
		for (BodyDeclaration member : members) {
			if (member instanceof ConstructorDeclaration) {
				if (constr != null) {
					throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(member),
							"Only maximum one constructor is allowed");
				} else {
					constr = (ConstructorDeclaration) member;
				}
			}
		}
		return constr;
	}

	private void printStaticMembersPrefix(ClassScope scope) {
// FIXME, find a solution for inline types (and unserstand how they work)
//		if (GeneratorConstants.SPECIAL_INLINE_TYPE.equals(n.getName())) {
//			printer.print(n.getName());
//			return;
//		}
		printer.print(stJsName(scope.getClazz()));
	}

	private void printMainMethodCall(ClassOrInterfaceDeclaration n, ClassScope scope) {
		List<BodyDeclaration> members = n.getMembers();
		for (BodyDeclaration member : members) {
			if (member instanceof MethodDeclaration) {
				MethodDeclaration methodDeclaration = (MethodDeclaration) member;
				if (isMainMethod(methodDeclaration)) {
					printer.printLn();
					printer.print("if (!stjs.mainCallDisabled) ");
					printStaticMembersPrefix(scope);
					printer.print(".main();");
				}
			}
		}
	}

	private boolean isMainMethod(MethodDeclaration methodDeclaration) {
		boolean isMainMethod = false;
		if (isStatic(methodDeclaration.getModifiers()) && "main".equals(methodDeclaration.getName())) {
			List<Parameter> parameters = methodDeclaration.getParameters();
			if ((parameters != null) && (parameters.size() == 1)) {
				Parameter parameter = parameters.get(0);
				if (parameter.getType() instanceof ReferenceType) {
					ReferenceType refType = (ReferenceType) parameter.getType();
					if ((refType.getArrayCount() == 1) && (refType.getType() instanceof ClassOrInterfaceType)) {
						String typeName = ((ClassOrInterfaceType) refType.getType()).getName();
						if ("String".equals(typeName) || "java.lang.String".equals(typeName)) {
							isMainMethod = true;
						}
					}
				}
			}
		}
		return isMainMethod;
	}

	@Override
	public void visit(EmptyTypeDeclaration n, NameScopeWalker scopeWalker) {
		printJavadoc(n.getJavaDoc(), scopeWalker);
		printer.print(";");
	}

	@Override
	public void visit(EnumConstantDeclaration n, NameScopeWalker scopeWalker) {
		// the enum constants are processed within the EnumDeclaration node. So this node should not be visited
		throw new IllegalStateException("Unexpected visit in a EnumConstantDeclaration node:" + n);

	}

	@Override
	public void visit(AnnotationDeclaration n, NameScopeWalker scopeWalker) {
		// skip

	}

	@Override
	public void visit(AnnotationMemberDeclaration n, NameScopeWalker scopeWalker) {
		// skip

	}

	@Override
	public void visit(EmptyMemberDeclaration n, NameScopeWalker scopeWalker) {
		printer.print(";");
	}

	@Override
	public void visit(InitializerDeclaration n, NameScopeWalker scopeWalker) {
		// should find a way to implement these blocks. For the moment forbid them
		throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
				"Initializing blocks are not supported by Javascript");
	}

	@Override
	public void visit(JavadocComment n, NameScopeWalker scopeWalker) {
		printer.print("/**");
		printer.print(n.getContent());
		printer.printLn("*/");
	}

	@Override
	public void visit(PrimitiveType n, NameScopeWalker scopeWalker) {
		throw new IllegalStateException("Unexpected visit in a PrimitiveType node:" + n);

	}

	@Override
	public void visit(VoidType n, NameScopeWalker scopeWalker) {
		throw new IllegalStateException("Unexpected visit in a VoidType node:" + n);
	}

	@Override
	public void visit(WildcardType n, NameScopeWalker scopeWalker) {
		throw new IllegalStateException("Unexpected visit in a WildcardType node:" + n);
	}

	@Override
	public void visit(ArrayAccessExpr n, NameScopeWalker scopeWalker) {
		n.getName().accept(this, scopeWalker);
		printer.print("[");
		n.getIndex().accept(this, scopeWalker);
		printer.print("]");
	}

	@Override
	public void visit(ArrayCreationExpr n, NameScopeWalker scopeWalker) {
		// skip the new type[][]
		n.getInitializer().accept(this, scopeWalker);
	}

	@Override
	public void visit(ArrayInitializerExpr n, NameScopeWalker scopeWalker) {
		printer.print("[");
		if (n.getValues() != null) {
			for (Iterator<Expression> i = n.getValues().iterator(); i.hasNext();) {
				Expression expr = i.next();
				expr.accept(this, scopeWalker);
				if (i.hasNext()) {
					printer.print(", ");
				}
			}

		}
		printer.print("]");
	}

	private Node parent(Node n) {
		return ((ASTNodeData) n.getData()).getParent();
	}

	private Node parent(Node n, int upLevel) {
		Node p = n;
		for (int i = 0; (i < upLevel) && (p != null); ++i) {
			p = parent(p);
		}
		return p;
	}

	private Node checkParent(Node n, Class<?> clazz) {
		Node parent = parent(n);
		if (parent == null) {
			return null;
		}
		return (clazz.isAssignableFrom(parent.getClass())) ? parent : null;
	}

	/**
	 * TODO - this can be done more generically
	 * 
	 * @param n
	 * @return true if the node is a direct child following the path:
	 *         //ObjectCreationExpr/InitializerDeclaration/BlockStmt/Child
	 */
	private boolean isInlineObjectCreationChild(Node n, int upLevel) {
		return isInlineObjectCreationBlock(parent(n, upLevel));

	}

	/**
	 * @param n
	 * @return true if the node is a block statement //ObjectCreationExpr/InitializerDeclaration/BlockStmt
	 */
	private boolean isInlineObjectCreationBlock(Node n) {
		if (!(n instanceof BlockStmt)) {
			return false;
		}
		Node p = null;
		if ((p = checkParent(n, InitializerDeclaration.class)) == null) {
			return false;
		}
		if ((p = checkParent(p, ObjectCreationExpr.class)) == null) {
			return false;
		}
		return true;
	}

	@Override
	public void visit(AssignExpr n, NameScopeWalker scopeWalker) {
		if (isInlineObjectCreationChild(n, 2)) {
			if (n.getTarget() instanceof FieldAccessExpr) {
				// in inline object creation "this." should be removed
				printer.print(((FieldAccessExpr) n.getTarget()).getField());
			} else {
				n.getTarget().accept(this, scopeWalker);
			}
			printer.print(" ");
			switch (n.getOperator()) {
			case assign:
				printer.print(":");
				break;
			default:
				throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
						"Cannot have this assign operator inside an inline object creation block");
			}
			printer.print(" ");
			n.getValue().accept(this, scopeWalker);
			return;
		}

		n.getTarget().accept(this, scopeWalker);
		printer.print(" ");
		switch (n.getOperator()) {
		case assign:
			printer.print("=");
			break;
		case and:
			printer.print("&=");
			break;
		case or:
			printer.print("|=");
			break;
		case xor:
			printer.print("^=");
			break;
		case plus:
			printer.print("+=");
			break;
		case minus:
			printer.print("-=");
			break;
		case rem:
			printer.print("%=");
			break;
		case slash:
			printer.print("/=");
			break;
		case star:
			printer.print("*=");
			break;
		case lShift:
			printer.print("<<=");
			break;
		case rSignedShift:
			printer.print(">>=");
			break;
		case rUnsignedShift:
			printer.print(">>>=");
			break;
		}
		printer.print(" ");
		n.getValue().accept(this, scopeWalker);

	}

	@Override
	public void visit(BinaryExpr n, NameScopeWalker scopeWalker) {
		n.getLeft().accept(this, scopeWalker);
		printer.print(" ");
		switch (n.getOperator()) {
		case or:
			printer.print("||");
			break;
		case and:
			printer.print("&&");
			break;
		case binOr:
			printer.print("|");
			break;
		case binAnd:
			printer.print("&");
			break;
		case xor:
			printer.print("^");
			break;
		case equals:
			printer.print("==");
			break;
		case notEquals:
			printer.print("!=");
			break;
		case less:
			printer.print("<");
			break;
		case greater:
			printer.print(">");
			break;
		case lessEquals:
			printer.print("<=");
			break;
		case greaterEquals:
			printer.print(">=");
			break;
		case lShift:
			printer.print("<<");
			break;
		case rSignedShift:
			printer.print(">>");
			break;
		case rUnsignedShift:
			printer.print(">>>");
			break;
		case plus:
			printer.print("+");
			break;
		case minus:
			printer.print("-");
			break;
		case times:
			printer.print("*");
			break;
		case divide:
			printer.print("/");
			break;
		case remainder:
			printer.print("%");
			break;
		}
		printer.print(" ");
		n.getRight().accept(this, scopeWalker);
	}

	@Override
	public void visit(ClassExpr n, NameScopeWalker scopeWalker) {
		n.getType().accept(this, scopeWalker);
		printer.print(".prototype");
	}

	@Override
	public void visit(ConditionalExpr n, NameScopeWalker scopeWalker) {
		n.getCondition().accept(this, scopeWalker);
		printer.print(" ? ");
		n.getThenExpr().accept(this, scopeWalker);
		printer.print(" : ");
		n.getElseExpr().accept(this, scopeWalker);
	}

	@Override
	public void visit(EnclosedExpr n, NameScopeWalker scopeWalker) {
		printer.print("(");
		n.getInner().accept(this, scopeWalker);
		printer.print(")");
	}

	@Override
	public void visit(InstanceOfExpr n, NameScopeWalker scopeWalker) {
		n.getExpr().accept(this, scopeWalker);
		printer.print(".constructor ==  ");
		if (n.getType() instanceof ReferenceType) {
			// TODO : could be more generic
			ClassWrapper type = scopeWalker.getScope().resolveType(((ReferenceType) n.getType()).getType().toString());
			printer.print(stJsName(type));
		} else {
			throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
					"Do not know how to handle instanceof statement");
		}
		// n.getType().accept(this, scopeWalker);
	}

	@Override
	public void visit(IntegerLiteralMinValueExpr n, NameScopeWalker scopeWalker) {
		printer.print(n.getValue());
	}

	@Override
	public void visit(LongLiteralMinValueExpr n, NameScopeWalker scopeWalker) {
		printer.print(n.getValue());
	}

	@Override
	public void visit(NullLiteralExpr n, NameScopeWalker scopeWalker) {
		printer.print("null");
	}

	@Override
	public void visit(FieldAccessExpr n, NameScopeWalker scopeWalker) {
		n.getScope().accept(this, scopeWalker);
		printer.print(".");
		printer.print(n.getField());
	}

	@Override
	public void visit(final MethodCallExpr n, final NameScopeWalker scopeWalker) {
		Collection<Method> methods = scopeWalker.getScope().resolveMethods(n.getName());
		if (true  /*FIXME !specialMethodHandlers.handleMethodCall(this, n, qname, scopeWalker)*/) {
			if (methods != null) {
				
				/*
				 * TODO : resolve not only the list of methods, but the actual method based on the arguments
				 */
				Method method = methods.iterator().next();
				ClassWrapper methodDeclaringClass = wrap(method.getDeclaringClass());
				if (Modifier.isStatic(method.getModifiers())) {
					printStaticFieldOrMethodAccessPrefix(methodDeclaringClass, true);
					printer.print(n.getName());
					printscopeWalkeruments(n.getArgs(), scopeWalker);
					return;
				} else {
					ClassScope thisClassScope = scopeWalker.getScope().closest(ClassScope.class);
					if (thisClassScope.getClazz().equals(methodDeclaringClass)) {
						// Non static reference to current enclosing type.
						printer.print("this.");
					} else if (methodDeclaringClass.isParentClassOf(thisClassScope.getClazz())) {
						// Non static reference to parent type
						printer.print("this._super");
						printscopeWalkeruments(Collections.singleton("\"" + n.getName() + "\""), n.getArgs(),
								Collections.<String> emptyList(), scopeWalker);
						return;
					} else if (n.getScope() != null) {
						n.getScope().accept(this, scopeWalker);
						printer.print(".");
					}
					printer.print(n.getName());
					printscopeWalkeruments(n.getArgs(), scopeWalker);
					
				}
			} else {
				// FIXME : should not exist anymore, we will always resolve methods
				if (n.getScope() != null) {
					n.getScope().accept(this, scopeWalker);
					printer.print(".");
				}
				printer.print(n.getName());
				printscopeWalkeruments(n.getArgs(), scopeWalker);
			}
		}
	}

	private void printStaticFieldOrMethodAccessPrefix(ClassWrapper type, boolean addDot) {
		if (!isGlobal(type)) {
			printer.print(stJsName(type));
			if (addDot) {
				printer.print(".");
			}
		}
	}
	

	private boolean isGlobal(ClassWrapper clazz) {
		return ClassUtils.hasAnnotation(clazz, GlobalScope.class.getName());
	}

	@Override
	public void visit(NameExpr n, NameScopeWalker scopeWalker) {
		if (GeneratorConstants.SPECIAL_THIS.equals(n.getName())) {
			printer.print("this");
			return;
		}
		/* FIXME: Do I need more context?
		QualifiedName<IdentifierName> qname = scopeWalker.resolveIdentifier(n);
		if (qname != null) {
			if (qname.isStatic()) {
				printStaticFieldOrMethodAccessPrefix(n, scopeWalker, qname, true);
			} else {
				qname.getScope().visit(new NameScope.EmptyVoidNameScopeVisitor(false) {
					@Override
					public void caseTypeScope(TypeScope typeScope) {
						printer.print("this.");
					}

					@Override
					public void caseParentTypeScope(ParentTypeScope parentTypeScope) {
						// prefix with this fields that are accesses directly from the super class
						printer.print("this.");
					}
				});
			}
		}
	*/
		printer.print(n.getName());
	}

	@Override
	public void visit(QualifiedNameExpr n, NameScopeWalker scopeWalker) {
		n.getQualifier().accept(this, scopeWalker);
		printer.print(".");
		printer.print(n.getName());
	}

	@Override
	public void visit(ThisExpr n, NameScopeWalker scopeWalker) {
		if (n.getClassExpr() != null) {
			n.getClassExpr().accept(this, scopeWalker);
			printer.print(".");
		}
		printer.print("this");
	}

	@Override
	public void visit(SuperExpr n, NameScopeWalker scopeWalker) {
		throw new IllegalStateException("The [super] node should've been already handled:" + n);
	}

	@Override
	public void visit(UnaryExpr n, NameScopeWalker scopeWalker) {
		switch (n.getOperator()) {
		case positive:
			printer.print("+");
			break;
		case negative:
			printer.print("-");
			break;
		case inverse:
			printer.print("~");
			break;
		case not:
			printer.print("!");
			break;
		case preIncrement:
			printer.print("++");
			break;
		case preDecrement:
			printer.print("--");
			break;
		}

		n.getExpr().accept(this, scopeWalker);

		switch (n.getOperator()) {
		case posIncrement:
			printer.print("++");
			break;
		case posDecrement:
			printer.print("--");
			break;
		}
	}

	@Override
	public void visit(SingleMemberAnnotationExpr n, NameScopeWalker scopeWalker) {
		// skip

	}

	@Override
	public void visit(NormalAnnotationExpr n, NameScopeWalker scopeWalker) {
		// skip

	}

	@Override
	public void visit(MemberValuePair n, NameScopeWalker scopeWalker) {
		// XXX: not very sure when this occurs
		printer.print(n.getName());
		printer.print(" = ");
		n.getValue().accept(this, scopeWalker);
	}

	@Override
	public void visit(ExplicitConstructorInvocationStmt n, NameScopeWalker scopeWalker) {
		if (n.isThis()) {
			// This should not happen as another constructor is forbidden
			throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
					"Only one constructor is allowed");
		}

		ClassScope classScope = (ClassScope) scopeWalker.getScope();
		PreConditions.checkNotNull(classScope);
		if (classScope.getClazz().getSuperclass().isDefined()) {
			// avoid useless call to super() when the super class is Object
			printer.print("this._super");
			printscopeWalkeruments(Collections.singleton("null"), n.getArgs(), Collections.<String> emptyList(), scopeWalker);
			printer.print(";");
		}
	}

	@Override
	public void visit(TypeDeclarationStmt n, NameScopeWalker scopeWalker) {
		n.getTypeDeclaration().accept(this, scopeWalker);
	}

	@Override
	public void visit(AssertStmt n, NameScopeWalker scopeWalker) {
		throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(n),
				"Assert statement is not supported by Javascript");
	}

	private void checkAssignStatement(Statement s, NameScopeWalker scopeWalker) {
		if (s instanceof ExpressionStmt) {
			if (((ExpressionStmt) s).getExpression() instanceof AssignExpr) {
				return;
			}
		}
		throw new JavascriptGenerationException(scopeWalker.getContext().getInputFile(), new SourcePosition(s),
				"Only assign expression are allowed in an object creation block");
	}

	@Override
	public void visit(BlockStmt n, NameScopeWalker scopeWalker) {
		printer.printLn("{");
		if (n.getStmts() != null) {
			printer.indent();
			for (int i = 0; i < n.getStmts().size(); ++i) {
				Statement s = n.getStmts().get(i);
				printComments(s, scopeWalker);
				if (isInlineObjectCreationChild(s, 1)) {
					checkAssignStatement(s, scopeWalker);
				}
				s.accept(this, scopeWalker);
				if (isInlineObjectCreationChild(s, 1) && (i < (n.getStmts().size() - 1)) && (n.getStmts().size() > 1)) {
					printer.print(",");
				}
				printer.printLn();
			}
			printer.unindent();
		}
		printer.print("}");

	}

	@Override
	public void visit(LabeledStmt n, NameScopeWalker scopeWalker) {
		printer.print(n.getLabel());
		printer.print(": ");
		n.getStmt().accept(this, scopeWalker);
	}

	@Override
	public void visit(EmptyStmt n, NameScopeWalker scopeWalker) {
		printer.print(";");
	}

	@Override
	public void visit(ExpressionStmt n, NameScopeWalker scopeWalker) {
		n.getExpression().accept(this, scopeWalker);
		if (!isInlineObjectCreationChild(n, 1)) {
			printer.print(";");
		}
	}

	@Override
	public void visit(SwitchStmt n, NameScopeWalker scopeWalker) {
		printer.print("switch(");
		n.getSelector().accept(this, scopeWalker);
		printer.printLn(") {");
		if (n.getEntries() != null) {
			printer.indent();
			for (SwitchEntryStmt e : n.getEntries()) {
				e.accept(this, scopeWalker);
			}
			printer.unindent();
		}
		printer.print("}");

	}

	@Override
	public void visit(SwitchEntryStmt n, NameScopeWalker scopeWalker) {
		if (n.getLabel() != null) {
			printer.print("case ");
			Variable selector = getSwitchSelectorType(n, scopeWalker.getScope());
			if (selector.getType().getClazz().isEnum()) {
				printer.print(stJsName(selector.getType()));
				printer.print(".");
			}
			n.getLabel().accept(this, scopeWalker);
			printer.print(":");
		} else {
			printer.print("default:");
		}
		printer.printLn();
		printer.indent();
		if (n.getStmts() != null) {
			for (Statement s : n.getStmts()) {
				s.accept(this, scopeWalker);
				printer.printLn();
			}
		}
		printer.unindent();
	}

	private Variable getSwitchSelectorType(SwitchEntryStmt n, Scope scope) {
		ASTNodeData nodeData = (ASTNodeData) n.getData();
		SwitchStmt switchStatement = (SwitchStmt) nodeData.getParent();
		Expression selector = switchStatement.getSelector();
		/*
		 * TODO : need to resolve the type of the expression,
		 * which means visiting all possible expression types.
		 * For now, assume a NameExpr that would represent a variable
		 */
		return scope.resolveVariable(selector.toString());
	}

	@Override
	public void visit(BreakStmt n, NameScopeWalker scopeWalker) {
		printer.print("break");
		if (n.getId() != null) {
			printer.print(" ");
			printer.print(n.getId());
		}
		printer.print(";");
	}

	@Override
	public void visit(ReturnStmt n, NameScopeWalker scopeWalker) {
		printer.print("return");
		if (n.getExpr() != null) {
			printer.print(" ");
			n.getExpr().accept(this, scopeWalker);
		}
		printer.print(";");
	}

	@Override
	public void visit(ThrowStmt n, NameScopeWalker scopeWalker) {
		printer.print("throw ");
		n.getExpr().accept(this, scopeWalker);
		printer.print(";");
	}

	@Override
	public void visit(TryStmt n, NameScopeWalker scopeWalker) {
		printer.print("try ");
		n.getTryBlock().accept(this, scopeWalker);
		if (n.getCatchs() != null) {
			for (CatchClause c : n.getCatchs()) {
				c.accept(this, scopeWalker);
			}
		}
		if (n.getFinallyBlock() != null) {
			printer.print(" finally ");
			n.getFinallyBlock().accept(this, scopeWalker);
		}
	}

	@Override
	public void visit(CatchClause n, NameScopeWalker scopeWalker) {
		printer.print(" catch (");
		n.getExcept().accept(this, scopeWalker);
		printer.print(") ");
		n.getCatchBlock().accept(this, scopeWalker);
	}

}
