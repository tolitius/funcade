(ns funcade.tools)

(defn auth-header
  ([token]
   (auth-header token "Bearer"))
  ([token prefix]
   {"Authorization" (str prefix " " token)}))
