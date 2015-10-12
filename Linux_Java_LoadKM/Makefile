include linuxalan-env.mk

DOC_TITLE="Linux-based EEP stub"
DOC_PKGS=UK.ac.lancs.linuxsdyn

classes/%.class: src/%.java
	@mkdir -p classes
	@echo Making `echo $* | tr '/' '.'`
	@$(JAVAC) -classpath `echo /dev/null $(CLASSPATH) | tr ' ' ':'` -sourcepath src -d classes $<

all::	classes/UK/ac/lancs/linuxsdyn/LinuxBasedALAN.class \
	classes/UK/ac/lancs/linuxsdyn/RecordedNamingContextRegistry.class \
	classes/UK/ac/lancs/linuxsdyn/LinuxLibraryLoader.class \
	classes/UK/ac/lancs/linuxsdyn/LinuxProgramLoader.class \
	classes/UK/ac/lancs/linuxsdyn/BasicLinuxKernel.class \
	classes/UK/ac/lancs/linuxsdyn/ReadOnlyCORBARegistry.class \
	classes/UK/ac/lancs/linuxsdyn/ReadOnlyRMIRegistry.class \
	classes/UK/ac/lancs/linuxsdyn/RoutingWrapper.class \
	classes/UK/ac/lancs/linuxsdyn/PropertiesSubset.class

install:
	$(INSTALL) -d $(INSTALL_PATH)/lib
	cd classes ; $(JAR) cf0 $(INSTALL_PATH)/lib/linuxalan.jar *

docs:
	rm -rf javadoc
	mkdir -p javadoc
	$(JAVADOC) -classpath `echo /dev/null $(CLASSPATH) | tr ' ' ':'` -d javadoc -sourcepath src -windowtitle $(DOC_TITLE) $(DOC_PKGS)
	cd javadoc ; zip -r ../javadoc.zip *
	rcp javadoc.zip $(DOC_HOST):$(DOC_PATH).zip

tidy:
	-rm -f *~ core
	-find src/ -name "*~" | xargs rm -f

clean: tidy
	-rm -rf classes javadoc javadoc.zip
	-rm -f *.o *.class

blank: clean
