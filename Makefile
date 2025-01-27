.PHONY: clean aot jar tag outdated install deploy tree repl test

clean:
	rm -rf target
	rm -rf classes

aot:
	mkdir classes
	clj -e "(compile 'funcade.java-api)"

jar: clean aot tag
	clojure -A:jar

outdated:
	clojure -M:outdated

tag:
	clojure -A:tag

install: jar
	clojure -A:install

deploy: jar
	clojure -A:deploy

tree:
	mvn dependency:tree

repl:
	clojure -A:dev -A:repl

test:
	clojure -M:test
