#!/bin/bash
# S3: B1-B4 con AMBER (dynamic halt, modello oscnn) sulla copia isolata del codice S2.
BENCH=$1; LOGS=$2
AMBER_ARGS="-hmodel oscnn -hhost localhost -hport 5001 -w 100ms -r 100ms"
info() { echo "$1" >> $LOGS/run-info.txt; }
power() { echo "power_online=$(cat /sys/class/power_supply/A*/online) battery=$(cat /sys/class/power_supply/BAT*/capacity)% governor=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"; }
: > $LOGS/run-info.txt
info "$(date -Iseconds) start"; info "$(power)"
info "codice: d0b82c97 (S2) su copia isolata; pom con jmh-core 1.37-extended (AMBER) anche nel processor path"
info "AMBER: $AMBER_ARGS ; JPT service = jpt_service avviato in venv Python 3.9 (no Docker)"
info "$(java -version 2>&1 | head -1)"
cd $BENCH
mvn -q install -DskipTests -Djacoco.skip=true > $LOGS/install.log 2>&1; info "install_exit=$?"
mvn -q -Pbenchmark clean test-compile -Djacoco.skip=true > $LOGS/compile-library.log 2>&1; info "compile_library_exit=$?"
mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=$LOGS/library.cp > /dev/null 2>&1
info "$(date -Iseconds) library start"
java -cp target/test-classes:target/classes:$(cat $LOGS/library.cp) org.openjdk.jmh.Main $AMBER_ARGS \
  -rf json -rff $LOGS/library.json org.apache.commons.imaging > $LOGS/library.log 2>&1
info "library_exit=$? $(date -Iseconds)"; info "$(power)"
cd backend
mvn -q -Pbenchmark clean test-compile > $LOGS/compile-backend.log 2>&1; info "compile_backend_exit=$?"
mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=$LOGS/backend.cp > /dev/null 2>&1
java -cp target/test-classes:target/classes:$(cat $LOGS/backend.cp) org.openjdk.jmh.Main $AMBER_ARGS \
  -rf json -rff $LOGS/backend.json it.course.imaging > $LOGS/backend.log 2>&1
info "backend_exit=$? $(date -Iseconds)"; info "$(power)"
