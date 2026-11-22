/* Original reading passages written for this app. Studies described are illustrative, not real publications. */
window.KH=window.KH||{};
(function(){
const tf=(q,a,w)=>({t:'tfng',q,a,w}), yn=(q,a,w)=>({t:'yn',q,a,w});
const mc=(q,o,a,w)=>({t:'mcq',q,o,a,w}), gp=(q,a,w)=>({t:'gap',q,a:[].concat(a),w}), sl=(q,a,w)=>({t:'sel',q,a,w});
const TF='Choose TRUE if the statement agrees with the information, FALSE if it contradicts the information, or NOT GIVEN if there is no information on this.';
const YN='Choose YES if the statement agrees with the views of the writer, NO if it contradicts the views of the writer, or NOT GIVEN if it is impossible to say what the writer thinks.';
const G2='Complete the sentences. Write NO MORE THAN TWO WORDS from the passage for each answer.';
KH.READING=[
{id:'r1',mock:1,title:'Bees in the city',topic:'Biology · ecology',words:0,
paras:[
['A','For many years, scientists assumed that wild bees did best in the countryside, where flowers are plentiful and pesticides are, in theory, easier to avoid. A recent survey of twelve European cities challenged that view. Researchers counted wild bee species in city parks, on rooftops and along railway lines, then compared the results with counts from nearby farmland. In several cities, the urban sites supported at least as many species as the farmland.'],
['B','The explanation lies in the variety of habitat found in a city. Gardens, allotments and parks flower at different times of the year, so food is available from early spring until late autumn. Farmland, by contrast, often produces a single crop that blossoms for only a few weeks. Cities also tend to be warmer than the surrounding countryside, which allows some species to become active earlier in the year.'],
['C','Nesting is another important factor. Most wild bees do not live in hives. About seventy per cent nest in bare soil, and the rest use hollow stems, dead wood or gaps in walls. Tidy lawns and sealed pavements remove nesting sites for ground-nesting species, but cities often contain neglected corners, such as railway embankments and abandoned plots, that are left undisturbed for years. One research team found that a single strip of rough ground beside a railway line held more nests than an entire manicured park of the same size.'],
['D','However, the picture is not entirely positive. The survey also showed that the species found in cities were not the same as those in the countryside. Cities favoured small, adaptable bees that can feed on many kinds of flower, while larger specialists that depend on one particular plant were rare. A city with many species can therefore still be missing the ones that conservationists worry about most. In addition, the researchers noted that counts depend on how much effort is spent searching, and that some cities had been surveyed more thoroughly than others.'],
['E','Planners are already acting on the findings. In one city, the council now delays the mowing of road verges until late summer, and in another, new buildings must include a green roof planted with a mixture of flowering species. Volunteers have built "bee hotels" from drilled wood blocks, although the survey team cautioned that these mainly benefit a few common species and may spread disease if they are not cleaned regularly. The researchers\' advice is simple: leave some soil bare, let some plants flower, and resist the urge to tidy everything.']],
groups:[
{ins:'Questions 1-5. '+TF,qs:[
tf('The survey compared city sites with nearby farmland.',0,'Paragraph A: counts were compared with nearby farmland.'),
tf('Farmland sites in the survey used fewer pesticides than city sites.',2,'The text does not actually compare pesticide use. Not Given.'),
tf('Cities can be warmer than the surrounding countryside.',0,'Paragraph B: cities tend to be warmer.'),
tf('Most wild bees live in hives.',1,'Paragraph C: most wild bees do not live in hives.'),
tf('Bee hotels can be harmful if they are not cleaned.',0,'Paragraph E: may spread disease if not cleaned regularly.')]},
{ins:'Questions 6-9. '+G2,qs:[
gp('Farmland often produces a single ___ that blossoms for only a few weeks.',['crop'],'Paragraph B: a single crop.'),
gp('About seventy per cent of wild bees nest in ___.',['bare soil'],'Paragraph C: nest in bare soil.'),
gp('A strip of rough ground beside a ___ held more nests than a manicured park.',['railway line','railway'],'Paragraph C: beside a railway line.'),
gp('Cities favoured small, ___ bees that can feed on many kinds of flower.',['adaptable'],'Paragraph D: small, adaptable bees.')]},
{ins:'Questions 10-13. Which paragraph contains the following information? Choose the correct letter, A-E.',opts:['A','B','C','D','E'],qs:[
sl('a warning about the reliability of the counts',3,'D: counts depend on how much effort is spent searching.'),
sl('a reference to how the timing of flowering helps bees',1,'B: flowers at different times, food from spring to autumn.'),
sl('examples of changes made by city councils',4,'E: delayed mowing and green roofs.'),
sl('an explanation of why untidy places are valuable',2,'C: neglected corners left undisturbed give nesting sites.')]}]},

{id:'r2',mock:1,title:'Sleeping on it',topic:'Biology · neuroscience',
paras:[
['A','Many students believe that an all-night revision session is the best way to prepare for an exam. Sleep researchers disagree. Over the past few decades, laboratory studies have suggested that sleep is not simply a rest for the body. It appears to be an active period during which the brain organises what has been learned during the day.'],
['B','In a typical experiment, volunteers learn a list of word pairs in the evening. Half of them sleep normally, while the others stay awake all night. Both groups then sleep normally for two further nights, so that simple tiredness does not affect the results, and are tested on the word pairs. The group that slept on the first night usually remembers more pairs.'],
['C','Scientists think the explanation involves a process called consolidation. During deep sleep, patterns of brain activity that occurred during learning seem to be replayed, which may strengthen the connections between the cells involved. Memories for facts and events appear to benefit most from deep sleep, while physical skills, such as playing a piece of music, seem to benefit more from the lighter stages of sleep that occur late in the night.'],
['D','Timing may also matter. In a small trial involving sixty secondary school pupils, one group reviewed new vocabulary shortly before going to bed and another group reviewed it the next morning. Both groups were tested the following day. The first group scored slightly higher, but the difference had almost disappeared a week later. The researchers pointed out that the pupils had not been assigned to the groups at random, so factors such as motivation could have influenced the result.'],
['E','Sleep specialists do not claim that studying the night before an exam is pointless. Instead, they recommend planning revision earlier in the week and protecting a full night of sleep before the test. Caffeine late in the afternoon can delay sleep, and some studies suggest that bright screens in the hour before bed have the same effect, although the evidence is still developing. A short nap of up to twenty minutes can improve alertness, but its benefits for memory are less clear.'],
['F','There are limits to what the research can show. Most laboratory studies use simple material such as word lists, so it is uncertain how far the findings apply to complex subjects such as genetics or organic chemistry. In addition, people differ in how much sleep they need, and a rule of eight hours may not suit everybody. Nevertheless, the broad message is consistent: learning does not end when the book is closed.']],
groups:[
{ins:'Questions 14-18. Choose the correct heading for paragraphs A, C, D, E and F from the list of headings below. (Paragraph B is the example: iv.)',opts:['i  A cautious result from a small school trial','ii  Advice for protecting study time and sleep','iii  A common belief challenged by science','iv  How researchers tested the effect of sleep','v  Why findings may not apply to every learner or subject','vi  The brain\'s way of strengthening memories','vii  The dangers of caffeine'],qs:[
sl('Paragraph A',2,'A: scientists oppose the common belief (staying up late).'),
sl('Paragraph C',5,'C: consolidation, which means strengthening memories.'),
sl('Paragraph D',0,'D: a small experiment and a result that needs caution.'),
sl('Paragraph E',1,'E: advice for planning study and protecting sleep.'),
sl('Paragraph F',4,'F: limits of the results on complex material and differences between people.')]},
{ins:'Questions 19-22. Choose the correct letter.',qs:[
mc('In the typical experiment, why do both groups sleep normally for two further nights?',['To remove the effect of tiredness','To give the sleep group extra practice','To test a different memory skill','To compare two kinds of word list'],0,'B: so that simple tiredness does not affect the results.'),
mc('According to paragraph C, skills such as playing music',['depend mainly on deep sleep','seem to benefit from lighter sleep late in the night','are forgotten quickly','are replayed during learning'],1,'C: lighter stages late in the night.'),
mc('What did the researchers say about the school trial?',['It proved that morning review is useless','Its result should be treated with caution','It showed lasting benefits after a week','It was the largest trial ever carried out'],1,'D: not random, motivation could matter, and the difference disappeared after a week.'),
mc('Which is described as having less clear benefits for memory?',['Caffeine','Bright screens','Short naps','Early revision'],2,'E: nap benefits for memory are less clear.')]},
{ins:'Questions 23-26. '+YN,qs:[
yn('Sleep researchers generally support all-night revision.',1,'A: Sleep researchers disagree.'),
yn('Deep sleep is more important for facts than for physical skills.',0,'C: facts and events benefit most from deep sleep.'),
yn('The pupils in the school trial were chosen at random.',1,'D: had not been assigned at random.'),
yn('Most of the pupils slept more than eight hours.',2,'There is no information about the pupils\' sleeping hours. Not Given.')]}]},

{id:'r3',mock:1,title:'Fresh water from the sea',topic:'Environmental science',
paras:[
['A','Hundreds of millions of people live in regions where fresh water is scarce, and the problem is growing as populations rise and rainfall patterns change. One response is desalination, the removal of salt from seawater. The idea is not new: sailors boiled seawater to produce drinking water centuries ago. What has changed is the scale. Today, thousands of plants supply cities, industries and farms, particularly in dry coastal countries.'],
['B','Most modern plants use reverse osmosis. Seawater is forced at high pressure through a membrane that allows water molecules to pass but blocks most of the dissolved salt. The process is far more efficient than older methods that relied on heating the water, and the energy needed to produce a cubic metre of fresh water has fallen by roughly half over the past thirty years. Even so, energy remains the largest single cost, often accounting for a third to a half of the price of the water.'],
['C','Environmental concerns centre on the concentrated salty water left over, known as brine. If it is released carelessly, it can sink to the seabed and reduce oxygen levels, harming organisms that cannot move away. Engineers have responded by installing long pipes that spread the brine over a wide area and dilute it quickly. Some plants now go further and extract valuable minerals such as magnesium from the brine, although this is not yet profitable everywhere.'],
['D','Critics argue that desalination encourages complacency. If water seems unlimited, they say, there is less pressure to repair leaking pipes, which in some cities waste a quarter or more of the supply, or to persuade households to use less. Supporters reply that the technology is only one part of a broader strategy. In one dry region, for example, authorities combine desalinated water with recycled wastewater and strict limits on garden irrigation.'],
['E','Cost is the other major issue. Desalinated water is typically more expensive than water from rivers or wells, which makes it difficult for poorer countries to adopt. Falling membrane prices and the use of solar power are narrowing the gap, and small solar-powered units have been installed in remote island communities. However, a plant that relies on solar panels alone cannot operate at night unless it stores energy, and storage adds to the cost.'],
['F','Most experts conclude that desalination will be a necessary tool but not a universal solution. Where seawater is nearby and energy is affordable, it can protect a city against drought. Inland regions face a harder problem, because transporting water uphill over long distances uses so much energy that other measures are usually cheaper.']],
groups:[
{ins:'Questions 27-31. Complete the summary. Write ONE WORD ONLY from the passage for each answer.',summary:true,qs:[
gp('Modern desalination mostly relies on forcing seawater through a ___ at high pressure.',['membrane'],'B: a membrane.'),
gp('This is more efficient than methods that involve ___ the water.',['heating'],'B: relied on heating the water.'),
gp('However, ___ is still the largest single cost.',['energy'],'B: energy remains the largest single cost.'),
gp('The leftover ___ can harm life on the seabed.',['brine'],'C: known as brine.'),
gp('It does this by lowering ___ levels.',['oxygen'],'C: reduce oxygen levels.')]},
{ins:'Questions 32-35. Choose the correct letter.',qs:[
mc('What does the writer say about the history of desalination?',['It began with reverse osmosis','Sailors used a simple form of it long ago','It was invented to supply farms','It has always been cheap'],1,'A: sailors boiled seawater centuries ago.'),
mc('What have engineers done to reduce harm from brine?',['Stopped producing it','Moved plants inland','Spread it over a wide area using long pipes','Pumped it into deep wells'],2,'C: long pipes spread the brine and dilute it.'),
mc('Critics of desalination believe that it',['discourages efforts to save water','damages water pipes','cannot supply cities','is cheaper than recycling'],0,'D: encourages complacency.'),
mc('What is the writer\'s view in the final paragraph?',['Desalination will replace all other sources','Desalination is useful but not the answer everywhere','Inland regions should build more plants','Energy costs will soon disappear'],1,'F: necessary tool but not a universal solution.')]},
{ins:'Questions 36-40. '+TF,qs:[
tf('The energy needed per cubic metre of fresh water has roughly halved in thirty years.',0,'B: fallen by roughly half.'),
tf('Extracting minerals from brine is profitable in every plant.',1,'C: not yet profitable everywhere.'),
tf('A region that combines desalination with recycled wastewater is described.',0,'D: authorities combine desalinated water with recycled wastewater.'),
tf('Desalination is more popular in Asia than in Africa.',2,'There is no comparison between continents. Not Given.'),
tf('Transporting water uphill over long distances is usually cheaper than other measures.',1,'F: other measures are usually cheaper.')]}]},

{id:'r4',mock:2,title:'Coral reefs under pressure',topic:'Marine biology',
paras:[
['A','Coral reefs cover less than one per cent of the ocean floor, yet they are home to around a quarter of all marine species. Reef-building corals are animals that live in partnership with microscopic algae. The algae live inside the coral\'s tissue, supply it with most of its food through photosynthesis, and give it its colour.'],
['B','When the water becomes too warm, this partnership breaks down. The coral expels the algae and turns pale, an event known as bleaching. A bleached coral is not dead, but it is starving and more vulnerable to disease. If temperatures return to normal within a few weeks, many colonies can recover. If the heat lasts, they die.'],
['C','Large bleaching events have become more frequent in recent decades. In some regions, surveys show that reefs now face a major event about every six years, which leaves too little time for slow-growing corals to regrow between events. Scientists warn that the gaps between events are likely to shrink further if the oceans continue to warm.'],
['D','Not all corals respond in the same way. Branching species grow quickly but are fragile, whereas massive, boulder-shaped species grow slowly and tend to survive heat better. In one regional survey, reefs made mostly of boulder-shaped corals lost fewer colonies than reefs dominated by branching types. Some corals also host heat-tolerant algae, which may allow them to survive higher temperatures.'],
['E','There are reasons for cautious hope. Laboratories are breeding colonies selected for heat tolerance, and some of these have been transplanted to damaged reefs. Local action matters too. Reducing pollution and overfishing gives reefs a better chance of recovering, because healthy reefs with plenty of grazing fish recover faster: the fish eat the seaweed that would otherwise smother young coral.'],
['F','None of these measures can replace cuts in greenhouse gas emissions. Restoration projects work at a small scale, usually a few hectares, while reef systems stretch for thousands of kilometres. Most researchers therefore treat restoration as a way of buying time and protecting the most valuable sites, not as a cure.']],
groups:[
{ins:'Questions 1-5. '+G2,qs:[
gp('Reef-building corals live in partnership with microscopic ___.',['algae'],'A: microscopic algae.'),
gp('A bleached coral is starving because it has lost its main source of ___.',['food'],'A and B: algae supply most of its food.'),
gp('Bleaching happens when the water becomes too ___.',['warm'],'B: when the water becomes too warm.'),
gp('Fast-growing but fragile corals are described as ___ species.',['branching'],'D: branching species.'),
gp('Fish that graze on ___ help reefs to recover.',['seaweed'],'E: the fish eat the seaweed.')]},
{ins:'Questions 6-9. '+TF,qs:[
tf('Bleached corals are always dead.',1,'B: A bleached coral is not dead.'),
tf('Some regions may experience a major bleaching event about every six years.',0,'C: in some regions, about every six years.'),
tf('Boulder-shaped corals grow more slowly than branching corals.',0,'D: massive species grow slowly, and branching ones grow fast.'),
tf('Transplanting heat-tolerant colonies has restored reefs thousands of kilometres long.',1,'F: restoration works at a small scale, a few hectares.')]},
{ins:'Questions 10-13. Match each statement with the correct item, A-D.',opts:['A  Branching corals','B  Boulder-shaped corals','C  Heat-tolerant algae','D  Reducing local pollution'],qs:[
sl('lost fewer colonies in a regional survey',1,'D: reefs of boulder-shaped corals lost fewer colonies.'),
sl('may help some corals survive higher temperatures',2,'D: heat-tolerant algae.'),
sl('grow quickly',0,'D: branching species grow quickly.'),
sl('gives reefs a better chance of recovering',3,'E: reducing pollution and overfishing.')]}]},

{id:'r5',mock:2,title:'A short history of vaccination',topic:'Medicine · history of science',
paras:[
['A','Long before vaccines existed, people noticed that survivors of some diseases rarely caught them twice. In several parts of Asia and Africa, healers practised variolation: they scratched material from a smallpox sore into the skin of a healthy person, hoping to cause a mild illness that would protect against a severe one. It worked often enough to spread, but a small proportion of patients died from the procedure itself.'],
['B','In 1796 the English doctor Edward Jenner tested a safer idea. He had heard that dairy workers who caught cowpox, a mild disease of cattle, seemed to be protected from smallpox. He inoculated a boy with material from a cowpox sore and later exposed him to smallpox; the boy did not become ill. By today\'s standards the experiment would be considered unethical, but it led to the first widely used vaccine. The word "vaccine" itself comes from the Latin word for cow.'],
['C','For another century, nobody knew why vaccination worked. In the 1880s, Louis Pasteur showed that weakened forms of a germ could protect animals from the full disease, and his team developed a vaccine against rabies. Over the following decades, scientists learned to identify the specific germs that cause diseases and to grow them in the laboratory, which made large-scale production possible.'],
['D','Vaccination campaigns have had dramatic results. In 1980 the World Health Organization declared that smallpox had been eradicated, the only human disease so far to have been removed completely. Polio has been eliminated from most countries, and measles deaths have fallen sharply, although the disease returns whenever vaccination rates drop. High coverage matters because it also protects people who cannot be vaccinated, an effect known as community protection.'],
['E','New technologies are changing how vaccines are made. Traditional vaccines use weakened or inactivated germs. Newer types deliver only the genetic instructions for making a harmless piece of the germ, which the body\'s cells then produce, training the immune system to recognise it. This approach allows developers to design a candidate vaccine within days of identifying a new threat, although testing for safety and effectiveness still takes months.'],
['F','Even so, vaccines face a challenge of public trust. Surveys in many countries show that most parents support routine immunisation, but a vocal minority are hesitant, often because of rumours spread online. Health workers say that listening to specific worries, and explaining both the benefits and the rare side effects honestly, works better than dismissing concerns.']],
groups:[
{ins:'Questions 14-18. Which paragraph contains the following information? Choose the correct letter, A-F.',opts:['A','B','C','D','E','F'],qs:[
sl('a description of an early method that carried risk for patients',0,'A: variolation, and some patients died from the procedure.'),
sl('a reference to the importance of high vaccination coverage',3,'D: community protection.'),
sl('an explanation of how newer vaccines are designed',4,'E: genetic instructions.'),
sl('the origin of a scientific word',1,'B: vaccine from the Latin word for cow.'),
sl('a recommended way of responding to hesitant parents',5,'F: listening to specific worries.')]},
{ins:'Questions 19-22. Choose the correct letter.',qs:[
mc('Why did variolation spread despite its risks?',['It was cheaper than other methods','It often protected people from a severe illness','It was approved by European doctors','It used material from cattle'],1,'A: It worked often enough to spread.'),
mc('What did Jenner\'s experiment suggest?',['Exposure to cowpox might protect against smallpox','Smallpox came from cattle','Boys are more resistant than adults','Rabies could be prevented'],0,'B: cowpox seemed to protect from smallpox.'),
mc('Pasteur\'s work showed that',['weakened germs could protect against disease','smallpox was caused by cattle','vaccines were unnecessary','germs could not be grown in laboratories'],0,'C: weakened forms of a germ could protect.'),
mc('What does the writer say about measles?',['It has been completely eradicated','Deaths have fallen but it returns when coverage drops','It is never fatal','It only spreads in Asia'],1,'D: returns whenever vaccination rates drop.')]},
{ins:'Questions 23-26. '+YN,qs:[
yn('Jenner\'s experiment would be considered acceptable today.',1,'B: would be considered unethical.'),
yn('Smallpox is the only human disease to have been completely eradicated so far.',0,'D: the only human disease so far.'),
yn('Newer types of vaccine can be tested more quickly than traditional ones.',2,'E: testing still takes months, but there is no comparison with the traditional method. Not Given.'),
yn('Dismissing parents\' concerns is an effective way to build trust.',1,'F: listening works better than dismissing.')]}]},

{id:'r6',mock:2,title:'Growing food upwards',topic:'Agricultural science',
paras:[
['A','A vertical farm grows crops in stacked layers inside a building, usually under LED lighting and without soil. The roots are supplied with water containing dissolved nutrients, a method called hydroponics, and sensors adjust temperature, humidity and light for each variety. Because everything is controlled, a farm can harvest lettuce and herbs all year round, whatever the weather outside.'],
['B','Supporters point to several advantages. Vertical farms can use up to ninety per cent less water than field farming because the water is recycled, and they often need no pesticides because pests are kept out of sealed rooms. They can be built close to cities, which shortens the distance that fresh produce must travel and so reduces spoilage. In land-scarce countries with harsh climates, they offer a way to grow some food locally.'],
['C','The difficulty is cost. Lighting and cooling use large amounts of electricity, and the buildings are expensive to equip. As a result, vertical farms have so far concentrated on high-value crops such as leafy greens, herbs and strawberries. Staple crops such as wheat and rice, which provide most of the world\'s calories, need much more light and space for each kilogram produced, and growing them indoors is not currently economical.'],
['D','Several start-up companies have closed after failing to cover their energy bills, and critics argue that early optimism was exaggerated. Defenders reply that the industry is young. LED efficiency continues to improve, and some farms now buy electricity from wind and solar sources. One company in a cold northern country has even built its farm beside a factory and uses the factory\'s waste heat to warm the building.'],
['E','Researchers also see vertical farms as laboratories. Because conditions can be controlled precisely, plant scientists can test how a single factor, such as the colour of the light, changes the growth or flavour of a crop without interference from the weather. Findings from this work may eventually benefit ordinary greenhouses, which are cheaper to run.'],
['F','For the foreseeable future, therefore, vertical farming looks likely to complement rather than replace conventional agriculture. It may be most valuable where land or fresh water is limited, and where consumers will pay slightly more for locally grown produce.']],
groups:[
{ins:'Questions 27-31. Complete the summary. Write NO MORE THAN TWO WORDS from the passage for each answer.',summary:true,qs:[
gp('Crops grow in stacked layers under ___ lighting.',['led'],'A: LED lighting.'),
gp('The roots receive water containing dissolved ___.',['nutrients'],'A: dissolved nutrients.'),
gp('Sensors adjust humidity, light and ___.',['temperature'],'A: temperature, humidity and light.'),
gp('Because the water is ___, much less is used than in field farming.',['recycled'],'B: the water is recycled.'),
gp('High electricity use means that farms concentrate on ___ crops such as leafy greens.',['high-value','high value'],'C: high-value crops.')]},
{ins:'Questions 32-35. Choose the correct letter.',qs:[
mc('What does the writer say about wheat and rice?',['They are already grown in many vertical farms','Growing them indoors is not currently economical','They need less light than lettuce','They are too expensive to sell'],1,'C: growing them indoors is not currently economical.'),
mc('Why did some start-up companies close?',['Customers rejected the produce','They could not pay their energy bills','Pests spread through the buildings','Land was too expensive'],1,'D: failing to cover their energy bills.'),
mc('How does one company use waste heat?',['To power the lights','To warm the farm building','To dry harvested crops','To cool the factory'],1,'D: uses the factory\'s waste heat to warm the building.'),
mc('Why are vertical farms described as laboratories?',['They are cheaper than greenhouses','Scientists can test single factors without weather interference','They grow staple crops for research','They can grow any crop'],1,'E: test a single factor without interference from the weather.')]},
{ins:'Questions 36-40. '+TF,qs:[
tf('Vertical farms can harvest crops throughout the year.',0,'A: harvest all year round.'),
tf('Vertical farms usually need large amounts of pesticide.',1,'B: they often need no pesticides.'),
tf('Strawberries are among the crops grown in vertical farms.',0,'C: leafy greens, herbs and strawberries.'),
tf('Most consumers refuse to pay extra for locally grown produce.',2,'There is no information about most consumers refusing. Not Given.'),
tf('The writer expects vertical farming to replace conventional agriculture.',1,'F: complement rather than replace.')]}]}
];
KH.READING.forEach(r=>{r.words=r.paras.map(p=>p[1]).join(' ').split(/\s+/).length;});
})();
