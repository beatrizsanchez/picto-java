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
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ImportContainer;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.ui.JavaElementLabels;
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
		final StringBuffer buffer = new StringBuffer();
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
		}
		return buffer.toString();
	}

	protected String processElement(IJavaElement jElement) throws JavaModelException {
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
				//Extension
				List<String> ok = new ArrayList<>();
				if (st.isClass()) {					
					if (st.getSuperclassName() !=null) {
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
						StringBuilder process = buildClass(imSt);
						if (process != null) {
							buffer.append(process);
							buffer.append(System.lineSeparator());								
						}
					}
				}
			}
		}
		return buffer.toString();
	}

	protected StringBuilder processFromPackage(SourceType jElement, Map<String, SourceType> map,
			String elemname) throws JavaModelException {
		if (!map.containsKey(elemname)) {
			String name = jElement.getPackageFragment().getElementName() +"."+ elemname;
			final IType findType = jElement.getJavaProject().findType(name);
			if (findType instanceof SourceType) {		
				return buildClass((SourceType) findType);
			}
		}
		return null;
	}

	protected StringBuilder buildClass(SourceType st)
			throws JavaModelException {
		final StringBuilder buffer = new StringBuilder();
		buffer.append(String.format("class %s {", st.getElementName()));
		buffer.append(System.lineSeparator());
		if (st.isInterface()) {
			buffer.append("<<interface>>");
			buffer.append(System.lineSeparator());				
		} else if (st.isEnum()) {
			buffer.append("<<enumeration>>");
			buffer.append(System.lineSeparator());
		} else if (JdtFlags.isAbstract(st)) {
			buffer.append("<<abstract>>");
			buffer.append(System.lineSeparator());
		}
		
		// Methods
		final IMethod[] methods = st.getMethods();
		for (IMethod m : methods) {
			StringBuffer methodSignature = new StringBuffer();
			JavaElementLabels.getMethodLabel(m, JavaElementLabels.ALL_DEFAULT | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.M_APP_RETURNTYPE, methodSignature);
			buffer.append(methodSignature.toString().replaceAll("[<>]", "~").replace(":", ""));
			buffer.append(System.lineSeparator());
		}
		return buffer.append("}");
	}
	
	protected String type(String a) {
		return a.replaceAll("[<>]", "~");
	}
	
	@Override
	public ViewTree getViewTree(IEditorPart editorPart) throws Exception {
		ViewTree viewTree = new ViewTree();
		viewTree.setPromise(new StaticContentPromise(execute()));
		viewTree.setFormat("text");
		return viewTree;
	}

	@Override
	public void dispose() {
		// do nothing
	}

}
