package org.epsilonlabs.java.views;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.source.PictoSource;
import org.eclipse.epsilon.picto.transformers.ExternalContentTransformation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ImportContainer;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.FileEditorInput;

@SuppressWarnings("restriction")
public class JavaPictoSource implements PictoSource {

	@Override
	public void showElement(String id, String uri, IEditorPart editor) {
		// do nothing
	}

	protected IJavaElement element;

	@Override
	public boolean supports(IEditorPart editorPart) {
		if (editorPart instanceof CompilationUnitEditor) {
			final FileEditorInput editorInput = (FileEditorInput) editorPart.getEditorInput();
			element = editorInput.getAdapter(IJavaElement.class);
			return true;
		}
		return false;
	}

	public String execute() {
		final StringBuilder buffer = new StringBuilder();
		buffer.append("classDiagram");
		buffer.append(System.lineSeparator());
		try {
			if (element instanceof ICompilationUnit) {
				ICompilationUnit cu = (ICompilationUnit) element;
				final IJavaElement[] children = cu.getChildren();
				for (IJavaElement ch : children) {
					buffer.append(processElement(ch));
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
			return "";
		}
		return buffer.toString();
	}

	protected StringBuilder processElement(IJavaElement jElement) throws JavaModelException {
		final StringBuilder buffer = new StringBuilder();
		Map<String, SourceType> map = new HashMap<>();
		if (jElement instanceof SourceType) {
			SourceType st = (SourceType) jElement;
			buffer.append(buildClass(st));
			buffer.append(System.lineSeparator());
			final String superclassName = st.getSuperclassName();
			if (superclassName != null) {
				final IType findType = jElement.getJavaProject().findType(superclassName);
				if (findType instanceof SourceType) {
					buffer.append(buildClass((SourceType) findType));
					buffer.append(System.lineSeparator());
				}
			}
			// Extension
			List<String> ok = new ArrayList<>();
			if (st.isClass() && st.getSuperclassName() != null) {
				String inheritance = st.getSuperclassName() + " <|-- " + st.getElementName();
				buffer.append(inheritance);
				buffer.append(System.lineSeparator());
				StringBuilder processFromPackage = processFromPackage(st, map, st.getSuperclassName());
				if (processFromPackage != null) {
					buffer.append(processFromPackage);
					buffer.append(System.lineSeparator());
				}
				ok.add(st.getSuperclassName());

			}

			// Implementation
			for (String interf : st.getSuperInterfaceNames()) {
				String realization = interf + " <|.. " + st.getElementName();
				buffer.append(realization);
				buffer.append(System.lineSeparator());
				if (!map.containsKey(st.getSuperclassName())) {
					StringBuilder process = processFromPackage(st, map, interf);
					if (process != null) {
						buffer.append(process);
						buffer.append(System.lineSeparator());
					}
				}
				ok.add(interf);
			}
			for (Entry<String, SourceType> e : map.entrySet()) {
				/*
				 * if (!ok.contains(e.getKey())) {
				 * 
				 * }
				 */
			}
			// Subtypes
			IType type= (IType)st;
			final ITypeHierarchy newTypeHierarchy = type.newTypeHierarchy(new NullProgressMonitor());
			final IType[] subclasses = newTypeHierarchy.getSubtypes(st);
			for (IType s : subclasses) {
				String inheritance = st.getElementName() + " <|-- " + s.getElementName();
				buffer.append(inheritance);
				buffer.append(System.lineSeparator());
				if (s instanceof SourceType) {						
					StringBuilder process = buildClass((SourceType)s);
					if (process != null) {
						buffer.append(process);
						buffer.append(System.lineSeparator());
					}
				}
			}

		} else if (jElement instanceof ImportContainer) {
			ImportContainer ic = (ImportContainer) jElement;
			final IJavaElement[] children = ic.getChildren();
			for (IJavaElement c : children) {
				final String importFullyQualifiedName = c.getElementName();
				if (importFullyQualifiedName.startsWith("org.epsilonlabs.modelflow")) {
					final IJavaElement e = jElement.getJavaProject().findType(importFullyQualifiedName);
					if (e instanceof SourceType) {
						final String[] split = c.getElementName().split("\\.");
						final SourceType imSt = (SourceType) e;
						map.put(split[split.length - 1], imSt);
						/*StringBuilder process = buildClass(imSt, false);
						if (process != null) {
							buffer.append(process);
							buffer.append(System.lineSeparator());
						}*/
					}
				}
			}
		}
		return buffer;
	}
	
	protected StringBuilder buildClass(SourceType st) throws JavaModelException {
		return buildClass(st, true);
	}

	protected StringBuilder processFromPackage(SourceType jElement, Map<String, SourceType> map, String elemname)
			throws JavaModelException {
		if (!map.containsKey(elemname)) {
			String name = jElement.getPackageFragment().getElementName() + "." + elemname;
			final IType findType = jElement.getJavaProject().findType(name);
			if (findType instanceof SourceType) {
				return buildClass((SourceType) findType);
			}
		}
		return null;
	}

	protected StringBuilder buildClass(SourceType st, boolean includeMethods) throws JavaModelException {
		final StringBuilder buffer = new StringBuilder();
		buffer.append(String.format("class %s {", st.getElementName()));
		buffer.append(System.lineSeparator());
		if (st.isInterface()) {
			buffer.append("<<Interface>>");
			buffer.append(System.lineSeparator());
		} else if (st.isEnum()) {
			buffer.append("<<enumeration>>");
			buffer.append(System.lineSeparator());
		} else if (JdtFlags.isAbstract(st)) {
			buffer.append("<<abstract>>");
			buffer.append(System.lineSeparator());
		}

		// Methods
		if (includeMethods) {
			final IMethod[] methods = st.getMethods();
			for (IMethod m : methods) {
				if (!JdtFlags.isPrivate(m)){				
					StringBuffer methodSignature = new StringBuffer();
					if (st.isClass()) {	
						if (JdtFlags.isPublic(m)) {
							methodSignature.append("+");
						} else  if (JdtFlags.isProtected(m)) {
							//methodSignature.append("#");
						}
					}
					JavaElementLabels.getMethodLabel(m, JavaElementLabels.ALL_DEFAULT | JavaElementLabels.M_PARAMETER_NAMES
							| JavaElementLabels.M_APP_RETURNTYPE, methodSignature);
					
					String replacement = "";
					if (st.isClass()) {					
						replacement = JdtFlags.isAbstract(m) ? "*" : (JdtFlags.isStatic(m) ? "$" : "");
					} 
					String signature = methodSignature.toString();
					final String[] split = signature.split("<");
					if (split.length>1) {
						signature = String.format("%s<%s>", split[0], split[1]/*.replace("(.*)( extends )(.*)", "$3")*/);
					}
					signature = signature.toString()
							.replaceAll("[<>]", "~") // In Mermaid generics use this character
							.replace(":", replacement) // Mermaid adds the colon
							.replace("~?", replacement) // Mermaid adds the colon
							.replaceAll("([?]( extends )?)", "") // question marks break the script
							;
					buffer.append(signature);
					buffer.append(System.lineSeparator());
				}
			}
		}
		return buffer.append("}");
	}

	@Override
	public ViewTree getViewTree(IEditorPart editorPart) throws Exception {
		//final StaticContentPromise content = new StaticContentPromise(text);
		//viewTree.setPromise();
		final String text = execute();
		
		ViewTree viewTree = new ViewTree();
		viewTree.setContent(mermaid(text));
		viewTree.setFormat("html");
		return viewTree;
	}

	private ViewContent mermaid(String text){
		final ViewContent content = new ViewContent("text", text);
		String html = null;
		String mmdString = content.getText();
		try {
			final Path mmd = ExternalContentTransformation.createTempFile("mm", mmdString.getBytes());
			final Path imgTmp = mmd.getParent().resolve(mmd.getFileName()+".svg");
			String program = "/usr/local/bin/mmdc";
			html = new String(new ExternalContentTransformation(
				imgTmp, program, "-i", mmd, "-o", imgTmp
			).call());
			return new ViewContent("svg", html, content);
		}
		catch (IOException iox) {
		}
		html = "<!DOCTYPE html>\n" + 
				"<html>\n" + 
				"<head>\n" +
				"<meta charset=\"UTF-8\"/>"+
				"  <title>A Meaningful Page Title</title></head>"
				+ "<body>"
				+ "<div class=\"mermaid\">\n" + mmdString.replaceAll("<", "&lt;").replaceAll(">", "&gt;") + "\n</div>"
				+ "<script src=\"https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js\"></script>\n" 
				+ "<script>mermaid.initialize({startOnLoad:true});</script>"
						+ "</body>"
						+ "</html>";
		return new ViewContent("html", html, content);	

	}
	
	@Override
	public void dispose() {
		// do nothing
	}

}
