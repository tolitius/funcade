(ns funcade.java-api
  (:require [funcade.core :as f]
            [funcade.tools :as t]))

(gen-class
  :name tolitius.Funcade
  :methods [^{:static true} [wakeTokenMaster [String java.util.Map] funcade.core.TokenMaster]
            ^{:static true} [currentToken [funcade.core.TokenMaster] String]
            ^{:static true} [stopTokenMaster [funcade.core.TokenMaster] void]])

(defn -wakeTokenMaster [tname config]
  (if (and (t/value? tname)
           (t/value? config))
    (f/wake-token-master (keyword tname)
                         (t/fmk config keyword))
    (throw (ex-info "can't wake a token master" {:reason "token name or config map should have values"
                                                 :token-name tname
                                                 :config config}))))

(defn -currentToken [tmaster]
  (when-not (nil? tmaster)
    (f/current-token tmaster)))

(defn -stopTokenMaster [tmaster]
  (when-not (nil? tmaster)
    (f/stop tmaster)))
