S3MF = s3.mf
S3JAR = jsnobol3.jar
CLASSDIR = classes
S3MAIN = jsnobol3.Snobol3
SRCDIR = src
MAINDIR = src/main
TESTDIR = ${SRCDIR}/tests
EXAMPLEDIR = ${SRCDIR}/examples

VMSRC =\
${MAINDIR}/jsnobol3/ArgType.java \
${MAINDIR}/jsnobol3/AST.java \
${MAINDIR}/jsnobol3/AstType.java \
${MAINDIR}/jsnobol3/Call.java \
${MAINDIR}/jsnobol3/S3Compiler.java \
${MAINDIR}/jsnobol3/Constants.java \
${MAINDIR}/jsnobol3/Debug.java \
${MAINDIR}/jsnobol3/Define.java \
${MAINDIR}/jsnobol3/Error.java \
${MAINDIR}/jsnobol3/EvalStack.java \
${MAINDIR}/jsnobol3/Frame.java \
${MAINDIR}/jsnobol3/Function.java \
${MAINDIR}/jsnobol3/FunctionCompiler.java \
${MAINDIR}/jsnobol3/Label.java \
${MAINDIR}/jsnobol3/Lexer.java \
${MAINDIR}/jsnobol3/Modes.java \
${MAINDIR}/jsnobol3/Operator.java \
${MAINDIR}/jsnobol3/Parser.java \
${MAINDIR}/jsnobol3/Pass1.java \
${MAINDIR}/jsnobol3/Pass2.java \
${MAINDIR}/jsnobol3/Pass3.java \
${MAINDIR}/jsnobol3/PatternOp.java \
${MAINDIR}/jsnobol3/Primitive.java \
${MAINDIR}/jsnobol3/Program.java \
${MAINDIR}/jsnobol3/S3Reader.java \
${MAINDIR}/jsnobol3/Scope.java \
${MAINDIR}/jsnobol3/Token.java \
${MAINDIR}/jsnobol3/TokenType.java \
${MAINDIR}/jsnobol3/Var.java \
${MAINDIR}/jsnobol3/VM.java

INTERPSRC =\
${MAINDIR}/jsnobol3/AbstractDebug.java \
${MAINDIR}/jsnobol3/AbstractDebugPoint.java \
${MAINDIR}/jsnobol3/CharStream.java \
${MAINDIR}/jsnobol3/CharStreamSequence.java \
${MAINDIR}/jsnobol3/Factory.java \
${MAINDIR}/jsnobol3/Main.java \
${MAINDIR}/jsnobol3/Parameters.java \
${MAINDIR}/jsnobol3/ParseArgs.java \
${MAINDIR}/jsnobol3/Pos.java \
${MAINDIR}/jsnobol3/QuotedString.java \
${MAINDIR}/jsnobol3/Ref.java \
${MAINDIR}/jsnobol3/Snobol3.java \
${MAINDIR}/jsnobol3/StringBufferReader.java \
${MAINDIR}/jsnobol3/Util.java \
${MAINDIR}/jsnobol3/abstractbody.java \
${MAINDIR}/jsnobol3/override.java \
${MAINDIR}/jsnobol3/subclassdefined.java

SRC = ${VMSRC} ${INTERPSRC}

CLASSDIR = classes

src = ${SRC:%.java = src/main/%.java}
classes = ${SRC:%.java=%.class}

.PHONY: check tests examples

all: ${S3JAR}

clean:: cleantests cleanexamples
	rm -fr ${CLASSDIR} ${S3JAR} ${S3MF}

cleantests::
	rm -fr ${SRCDIR}/tests/outputs

cleanexamples::
	rm -fr ${SRCDIR}/examples/outputs

${S3JAR}: ${src} ${CLASSDIR}
	rm -f ${S3MF}
	echo 'Manifest-Version: 1.0' > ${S3MF}
	echo 'Main-Class:' "${S3MAIN}" >> ${S3MF}
	echo '' >> ${S3MF}
	javac -d ${CLASSDIR} -classpath "${CLASSDIR}" ${src}
	jar -mcf ${S3MF} ${S3JAR} -C ${CLASSDIR} jsnobol3

${CLASSDIR}:
	mkdir ${CLASSDIR}

##################################################

check: tests

TESTS=\
test1 \
test2 \
test3 \
test4 \
test5 \
test6 \
test7 \
test8 \
test9 \
test10

TESTBASE = ${TESTDIR}/baseline
TESTOUT = ${TESTDIR}/outputs
tests: ${S3JAR} ${TESTS:%=${TESTDIR}/%.s3}
	@rm -fr ${TESTOUT}
	@mkdir -p ${TESTOUT}
	@for t in  ${TESTS} ; do (\
	    java -jar "${S3JAR}" ${TESTDIR}/$${t}.s3 > ${TESTOUT}/$${t}.txt ;\
	    if diff -wBb ${TESTBASE}/$${t}.txt ${TESTOUT}/$${t}.txt >/dev/null;\
	    then \
		echo "*** PASS: $$t"; \
	    else \
		echo "*** FAIL: $$t"; \
	    fi; \
	) done

EXAMPLES =\
abs  \
fact \
gdb  \
rfact

EXAMPLEBASE = ${EXAMPLEDIR}/baseline
EXAMPLEOUT = ${EXAMPLEDIR}/outputs
examples: ${S3JAR} ${EXAMPLES:%=${EXAMPLEDIR}/%.s3}
	@rm -fr ${EXAMPLEOUT}
	@mkdir -p ${EXAMPLEOUT}
	@for t in  ${EXAMPLES} ; do (\
	    java -jar "${S3JAR}" ${EXAMPLEDIR}/$${t}.s3 > ${EXAMPLEOUT}/$${t}.txt ;\
	    if diff -wBb ${EXAMPLEBASE}/$${t}.txt ${EXAMPLEOUT}/$${t}.txt >/dev/null;\
	    then \
		echo "*** PASS: $$t"; \
	    else \
		echo "*** FAIL: $$t"; \
	    fi; \
	) done
