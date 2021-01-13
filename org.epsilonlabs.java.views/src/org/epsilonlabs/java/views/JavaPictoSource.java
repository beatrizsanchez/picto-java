package org.epsilonlabs.java.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.source.PictoSource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ImportContainer;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.FileEditorInput;

@SuppressWarnings("restriction")
public class JavaPictoSource implements PictoSource{

	@Override
	public void showElement(String id, String uri, IEditorPart editor) {
		//do nothing
	}

	protected IJavaElement element;
	@Override
	public boolean supports(IEditorPart editorPart) {
		if (editorPart instanceof CompilationUnitEditor) {
			final FileEditorInput editorInput = (FileEditorInput) editorPart.getEditorInput();
			element = editorInput.getAdapter(IJavaElement.class);
			return true;
			//JavaModelUtil.getPackageFragmentRoot(element)
		}
		return false;
	}

	public String execute() {
		//final IJavaProject model = element.getJavaProject();
		String result = "classDiagram";
		result += System.lineSeparator();
		try {
			if (element instanceof ICompilationUnit) {
				ICompilationUnit cu = (ICompilationUnit) element;
				final IJavaElement[] children = cu.getChildren();
				for (IJavaElement ch : children) {
					result = processInterface(result, ch);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return result ;
	}

	protected String processInterface(String buffer, IJavaElement jElement) throws JavaModelException {
		Map<String, SourceType> map = new HashMap<>(); 
		if (jElement instanceof SourceType) {
			SourceType st = (SourceType) jElement;
				buffer += processInterfaceInternal(st);
				buffer += System.lineSeparator();								
				final String superclassName = st.getSuperclassName();
				if (superclassName != null) {					
					final IType findType = jElement.getJavaProject().findType(superclassName);
					if (findType instanceof SourceType) {					
						buffer += processInterfaceInternal((SourceType) findType);
						buffer += System.lineSeparator();								
					}
				}
				//Extension
				List<String> ok = new ArrayList<>();
				if (st.isClass()) {					
					if (st.getSuperclassName() !=null) {
						buffer += st.getSuperclassName() + " <|-- " + st.getElementName();						
						buffer += System.lineSeparator();
						buffer += processFromPackage(st, map, st.getSuperclassName());
						ok.add(st.getSuperclassName());
						buffer += System.lineSeparator();
					}
				}

				// Implementation
				for (String interf : st.getSuperInterfaceNames()) {
					buffer += interf + " <|.. " + st.getElementName();
					buffer += System.lineSeparator();
					if (!map.containsKey(st.getSuperclassName())) {
						buffer += processFromPackage(st, map, interf);
						buffer += System.lineSeparator();
					}
					ok.add(interf);
				}
				for (Entry<String, SourceType> e : map.entrySet()) {
					if (!ok.contains(e.getKey())) {
						
					}
				}
				
		} else if (jElement instanceof ImportContainer) {
			ImportContainer ic =(ImportContainer) jElement;
			final IJavaElement[] children = ic.getChildren();
			for (IJavaElement c : children) {				
				final String importFullyQualifiedName = c.getElementName();
				if (importFullyQualifiedName.startsWith("org.epsilonlabs.modelflow")) {					
					final IJavaElement e = jElement.getJavaProject().findType(importFullyQualifiedName);
					if (e instanceof SourceType) {
						final String[] split = c.getElementName().split("\\.");
						final SourceType imSt = (SourceType) e;
						map.put(split[split.length-1], imSt);
						buffer += processInterfaceInternal(imSt);
						buffer += System.lineSeparator();								
					}
				}
			}
		}
		return buffer;
	}

	/**
	 * @param buffer
	 * @param jElement
	 * @param map
	 * @param st
	 * @param ok
	 * @return
	 * @throws JavaModelException
	 */
	protected String processFromPackage(SourceType jElement, Map<String, SourceType> map,
			String elemname) throws JavaModelException {
		String buffer = "";
		if (!map.containsKey(elemname)) {
			String name = jElement.getPackageFragment().getElementName() +"."+ elemname;
			final IType findType = jElement.getJavaProject().findType(name);
			if (findType instanceof SourceType) {		
				buffer += processInterfaceInternal((SourceType) findType);
			}
		}
		return buffer;
	}

	/**
	 * @param buffer
	 * @param cu
	 * @param st
	 * @return
	 * @throws JavaModelException
	 */
	protected String processInterfaceInternal(SourceType st)
			throws JavaModelException {
		String buffer = "";
		buffer += "class ";
		buffer += st.getElementName();
		buffer += " {";
		buffer += System.lineSeparator();
		if (st.isInterface()) {
			buffer += "<<interface>>";
			buffer += System.lineSeparator();				
		} else if (st.isEnum()) {
			buffer += "<<enumeration>>";
			buffer += System.lineSeparator();
		} 
		final IMethod[] methods = st.getMethods();
		for (IMethod m : methods) {
			String methodSignature = "";
			methodSignature += "+" + m.getElementName() + "(";
			
			// Parameters
			final ILocalVariable[] parameters = m.getParameters();
			for (int i=0; i< parameters.length; i++) {
				final ILocalVariable p = parameters[i];
				methodSignature += p.getElementName() + " : " + type(p.getTypeSignature());
				if (i != parameters.length-1) {
					methodSignature += ", ";
				}
			}
			methodSignature += ")";
			if (m.getReturnType().equals("V")) {
				methodSignature+= " void";
			}  else {
				methodSignature += " " + type(m.getReturnType());
			}
			buffer += methodSignature.replace(">", "~").replace("<", "~");
			buffer += System.lineSeparator();
		}
		buffer += "}";
		return buffer;
	}
	
	protected String type(String a) {
		return a.replace("Q", "").replace(";", "");
	}
	
	@Override
	public ViewTree getViewTree(IEditorPart editorPart) throws Exception {
		ViewTree viewTree = new ViewTree();
		viewTree.setPromise(new StaticContentPromise(execute()));
		//viewTree.setFormat("graphviz-dot");
		viewTree.setFormat("text");
		return viewTree;
	}

	@Override
	public void dispose() {
		// do nothing
	}

}
