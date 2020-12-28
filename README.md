
<div id="logo" style="margin-left:2cm">
<img width="35%" src="src/main/resources/logoAce.png" alt="logo"/>
</div>

ACE (AbsCon Essence) is an open-source Java-written Constraint Solver, developed by Christophe Lecoutre (CRIL).

ACE focuses on:
- integer variables, including 0/1 (Boolean) variables,
- state-of-the-art table constraints, including ordinary, short, and smart table constraints,
- popular global constraints (allDifferent, count, element, cardinality, etc.),
- search heuristics (wdeg, impact, activity, last-conflict, cos, ...),
- mono-criterion optimization

ACE is distributed under License MIT


## Building a JAR

1. clone the repository:  
   `git clone https://github.com/xcsp3team/ace.git`
1. change directory:  
   `cd ace`
1. run Gradle (of course, you need Gradle to be installed):  
   `gradle build -x test`  
1. test the JAR:  
   `java -jar build/libs/ACE-YY-MM.jar`   
where you give the right values for YY and MM.
If the usage of ACE is displayed, you are fine. 

With this JAR, you can run ACE on any XCSP3 instance.

## Running Unit Tests

1. run Gradle:  
   `gradle test`
1. see results in:  
   `ace/build/reports/tests/index.html`
